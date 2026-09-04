package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.InvoiceCreateRequest;
import ru.anyforms.dto.payment.InvoiceDTO;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffReceipt;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.InvoiceService;
import ru.anyforms.service.payment.TinkoffService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static ru.anyforms.service.payment.impl.TinkoffPaymentSupport.blankToNull;
import static ru.anyforms.service.payment.impl.TinkoffPaymentSupport.truncate;

@Service
@RequiredArgsConstructor
@Slf4j
class InvoiceServiceImpl implements InvoiceService {

    private static final String PAYMENT_SUBJECT = "commodity";
    private static final String DEFAULT_ITEM_NAME = "Оплата по счёту";

    private final TinkoffService tinkoffService;
    private final TinkoffPaymentSupport tinkoffSupport;
    private final GetterTransaction getterTransaction;
    private final SaverTransaction saverTransaction;

    @Override
    @Transactional
    public InvoiceDTO create(InvoiceCreateRequest request) {
        long amountKopecks = parseAmountToKopecks(request.getAmount());
        String itemName = blankToNull(request.getDescription()) != null
                ? truncate(request.getDescription().trim(), TinkoffPaymentSupport.ITEM_NAME_MAX_LENGTH)
                : DEFAULT_ITEM_NAME;
        String description = "Счёт anyforms: " + request.getName().trim();

        TinkoffInitRequest initRequest = tinkoffSupport.initRequest(amountKopecks, UUID.randomUUID().toString(), description)
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
                .status(tinkoffSupport.resolveStatus(response.getStatus()))
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
        return tinkoffSupport.receipt(request.getEmail(), request.getPhone(),
                List.of(tinkoffSupport.receiptItem(itemName, amountKopecks, 1, PAYMENT_SUBJECT)));
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

}
