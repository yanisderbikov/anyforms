package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.ProductSalesDTO;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.ProductSalesRow;
import ru.anyforms.service.payment.SalesStatsService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
class SalesStatsServiceImpl implements SalesStatsService {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private static final List<String> TRAINING_PRODUCT_CODES = List.of(
            PaymentProduct.CODE_GUIDE,
            PaymentProduct.CODE_COURSE,
            PaymentProduct.CODE_COURSE_PERSONAL);

    private final GetterTransaction getterTransaction;

    @Override
    public List<ProductSalesDTO> getTrainingSales(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужны обе даты: from и to");
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата to раньше from");
        }

        Instant fromInstant = from.atStartOfDay(MSK).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(MSK).toInstant();

        Map<String, ProductSalesRow> rows = getterTransaction
                .getSalesByProductCodes(PaymentTransactionStatus.SUCCEEDED, TRAINING_PRODUCT_CODES,
                        fromInstant, toInstant)
                .stream()
                .collect(Collectors.toMap(ProductSalesRow::getProductCode, Function.identity()));

        List<ProductSalesDTO> sales = TRAINING_PRODUCT_CODES.stream()
                .map(code -> {
                    ProductSalesRow row = rows.get(code);
                    return new ProductSalesDTO(
                            code,
                            row != null ? row.getQuantity() : 0L,
                            row != null ? row.getAmountKopecks() : 0L);
                })
                .toList();

        log.info("Продажи обучения за {} — {}: {}", from, to,
                sales.stream().map(s -> s.code() + "=" + s.quantity()).collect(Collectors.joining(", ")));
        return sales;
    }
}
