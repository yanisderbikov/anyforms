package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.InvoiceCreateRequest;
import ru.anyforms.dto.payment.InvoiceDTO;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffReceipt;
import ru.anyforms.dto.payment.tinkoff.TinkoffReceiptItem;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.InvoiceService;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.TinkoffService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class InvoiceServiceImpl implements InvoiceService {

    private static final String PAYMENT_MODE = "full_payment";
    private static final String PAYMENT_SUBJECT = "commodity";
    private static final String TINKOFF_PAY_TYPE_SINGLE_STAGE = "O";
    private static final String DEFAULT_ITEM_NAME = "Оплата по счёту";
    private static final int TINKOFF_ITEM_NAME_MAX_LENGTH = 128;

    private final TinkoffService tinkoffService;
    private final GetterTransaction getterTransaction;
    private final SaverTransaction saverTransaction;
    private final PaymentStatusConverter paymentStatusConverter;

    @Value("${payment.tinkoff.taxation}")
    private String tinkoffTaxation;

    @Value("${payment.tinkoff.tax}")
    private String tinkoffTax;

    @Value("${payment.tinkoff.notification-url}")
    private String tinkoffNotificationUrl;

    @Override
    @Transactional
    public InvoiceDTO create(InvoiceCreateRequest request) {
        long amountKopecks = parseAmountToKopecks(request.getAmount());
        String itemName = blankToNull(request.getDescription()) != null
                ? truncate(request.getDescription().trim(), TINKOFF_ITEM_NAME_MAX_LENGTH)
                : DEFAULT_ITEM_NAME;
        String description = "Счёт anyforms: " + request.getName().trim();

        TinkoffInitRequest initRequest = TinkoffInitRequest.builder()
                .amount(amountKopecks)
                .orderId(UUID.randomUUID().toString())
                .description(description)
                .payType(TINKOFF_PAY_TYPE_SINGLE_STAGE)
                .notificationURL(blankToNull(tinkoffNotificationUrl))
                .receipt(buildReceipt(request, itemName, amountKopecks))
                .build();

        TinkoffInitResponse response = tinkoffService.init(initRequest);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .provider(PaymentProvider.TINKOFF)
                .externalPaymentId(response.getPaymentId())
                .productCode(PaymentProduct.CODE_MANUAL_INVOICE)
                .amount(amountKopecks)
                .currency(Currency.RUB)
                .description(itemName)
                .email(blankToNull(request.getEmail()))
                .contactName(request.getName().trim())
                .contactPhone(request.getPhone().trim())
                .paymentUrl(response.getPaymentURL())
                .status(resolveStatus(response.getStatus()))
                .build();
        saverTransaction.save(transaction);

        log.info("Выставлен счёт {} на {} коп. для {}", response.getPaymentId(), amountKopecks, request.getName());
        return InvoiceDTO.from(transaction);
    }

    @Override
    public List<InvoiceDTO> recent(int limit) {
        return getterTransaction.getRecentByProductCode(PaymentProduct.CODE_MANUAL_INVOICE, limit).stream()
                .map(InvoiceDTO::from)
                .toList();
    }

    private TinkoffReceipt buildReceipt(InvoiceCreateRequest request, String itemName, long amountKopecks) {
        TinkoffReceiptItem item = TinkoffReceiptItem.builder()
                .name(itemName)
                .price(amountKopecks)
                .quantity(1)
                .amount(amountKopecks)
                .tax(tinkoffTax)
                .paymentMethod(PAYMENT_MODE)
                .paymentObject(PAYMENT_SUBJECT)
                .build();
        return TinkoffReceipt.builder()
                .email(blankToNull(request.getEmail()))
                .phone(blankToNull(request.getPhone()))
                .taxation(tinkoffTaxation)
                .items(List.of(item))
                .build();
    }

    private long parseAmountToKopecks(String amount) {
        String normalized = amount.replaceAll("[\\s\\u00A0₽]", "").replace(',', '.');
        try {
            long kopecks = new BigDecimal(normalized)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (kopecks <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма должна быть больше нуля");
            }
            return kopecks;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная сумма: " + amount);
        }
    }

    private PaymentTransactionStatus resolveStatus(String tinkoffStatus) {
        PaymentTransactionStatus status = paymentStatusConverter.fromTinkoff(tinkoffStatus);
        return status != null ? status : PaymentTransactionStatus.PENDING;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
