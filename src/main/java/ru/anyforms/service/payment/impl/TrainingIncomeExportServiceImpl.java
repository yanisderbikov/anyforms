package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.YooKassaPaymentResponse;
import ru.anyforms.integration.IncomeSheetsGateway;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.TrainingIncomeExportService;
import ru.anyforms.service.payment.YooKassaService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Выгрузка продаж обучения (гайд/курс/личное сопровождение) в гугл-таблицу доходов:
 * один лист на год оплаты, дедупликация по ID платежа у провайдера, уже выгруженные строки
 * никогда не меняются — в таблице остаётся снимок на момент выгрузки.
 * Для ЮKassa «чистыми» берём income_amount из API; Т-Касса сумму за вычетом комиссии
 * не отдаёт, поэтому колонка остаётся пустой.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class TrainingIncomeExportServiceImpl implements TrainingIncomeExportService {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter PAID_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SERVICE_YOOKASSA = "ЮKassa";
    private static final String SERVICE_TINKOFF = "Т-Касса";
    private static final int WINDOW_DAYS = 7;

    /** Колонка C — ID платежа у провайдера, ключ дедупликации */
    private static final int PAYMENT_ID_COLUMN_INDEX = 2;

    private static final List<Object> HEADER_ROW = List.of(
            "Дата оплаты (МСК)", "ID транзакции", "ID платежа", "Сервис", "Продукт", "Сумма ₽", "Чистыми ₽");

    private static final List<String> TRAINING_PRODUCT_CODES = List.of(
            PaymentProduct.CODE_GUIDE,
            PaymentProduct.CODE_COURSE,
            PaymentProduct.CODE_COURSE_PERSONAL);

    private final GetterTransaction getterTransaction;
    private final YooKassaService yooKassaService;
    private final IncomeSheetsGateway incomeSheetsGateway;

    @Override
    public int export() {
        ZonedDateTime nowMsk = ZonedDateTime.now(MSK);
        String currentYearSheet = sheetName(nowMsk.getYear());
        incomeSheetsGateway.ensureSheetExists(currentYearSheet, HEADER_ROW);

        Map<String, Set<String>> exportedBySheet = new HashMap<>();
        exportedBySheet.put(currentYearSheet, readExportedPaymentIds(currentYearSheet));

        // Первый запуск (лист года пустой) — догоняем весь текущий год, иначе окно в неделю,
        // чтобы упавшие запуски догонялись следующими
        Instant from = exportedBySheet.get(currentYearSheet).isEmpty()
                ? nowMsk.toLocalDate().withDayOfYear(1).atStartOfDay(MSK).toInstant()
                : nowMsk.minusDays(WINDOW_DAYS).toInstant();

        List<PaymentTransaction> candidates = getterTransaction.getByStatusAndProductCodesUpdatedBetween(
                PaymentTransactionStatus.SUCCEEDED, TRAINING_PRODUCT_CODES,
                from, nowMsk.toInstant());

        Map<String, List<List<Object>>> newRowsBySheet = new LinkedHashMap<>();
        for (PaymentTransaction transaction : candidates) {
            ZonedDateTime paidAt = transaction.getUpdatedAt().atZone(MSK);
            String sheet = sheetName(paidAt.getYear());
            Set<String> exported = exportedBySheet.computeIfAbsent(sheet, name -> {
                incomeSheetsGateway.ensureSheetExists(name, HEADER_ROW);
                return readExportedPaymentIds(name);
            });
            if (!exported.add(transaction.getExternalPaymentId())) {
                continue;
            }

            try {
                newRowsBySheet.computeIfAbsent(sheet, name -> new ArrayList<>())
                        .add(buildRow(transaction, paidAt));
            } catch (Exception e) {
                // Пропускаем — недельное окно дозапишет строку в следующие запуски
                exported.remove(transaction.getExternalPaymentId());
                log.warn("Платёж {} не выгружен, догоним следующим запуском: {}",
                        transaction.getExternalPaymentId(), e.getMessage());
            }
        }

        int appended = 0;
        for (Map.Entry<String, List<List<Object>>> entry : newRowsBySheet.entrySet()) {
            incomeSheetsGateway.appendRows(entry.getKey(), entry.getValue());
            appended += entry.getValue().size();
        }
        log.info("Выгрузка доходов обучения: кандидатов {}, дописали {} строк", candidates.size(), appended);
        return appended;
    }

    private List<Object> buildRow(PaymentTransaction transaction, ZonedDateTime paidAt) {
        boolean tinkoff = transaction.getProvider() == PaymentProvider.TINKOFF;
        return List.of(
                PAID_AT_FORMAT.format(paidAt),
                transaction.getId().toString(),
                transaction.getExternalPaymentId(),
                tinkoff ? SERVICE_TINKOFF : SERVICE_YOOKASSA,
                transaction.getProductCode(),
                transaction.getAmount() / 100.0,
                tinkoff ? "" : yooKassaIncomeRubles(transaction));
    }

    private double yooKassaIncomeRubles(PaymentTransaction transaction) {
        YooKassaPaymentResponse payment = yooKassaService.getPayment(transaction.getExternalPaymentId());
        if (payment.getIncomeAmount() == null || payment.getIncomeAmount().getValue() == null) {
            throw new IllegalStateException("ЮKassa ещё не отдала income_amount");
        }
        return new BigDecimal(payment.getIncomeAmount().getValue()).doubleValue();
    }

    private Set<String> readExportedPaymentIds(String sheetName) {
        List<List<Object>> rows = incomeSheetsGateway.readAllRows(sheetName);
        Set<String> paymentIds = new HashSet<>();
        // Первая строка — заголовок
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row != null && row.size() > PAYMENT_ID_COLUMN_INDEX && row.get(PAYMENT_ID_COLUMN_INDEX) != null) {
                String paymentId = row.get(PAYMENT_ID_COLUMN_INDEX).toString().trim();
                if (!paymentId.isEmpty()) {
                    paymentIds.add(paymentId);
                }
            }
        }
        return paymentIds;
    }

    private String sheetName(int year) {
        return year + " год";
    }
}
