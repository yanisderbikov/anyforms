package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.Amount;
import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.PurchaseRequest;
import ru.anyforms.dto.payment.YooKassaPaymentResponse;
import ru.anyforms.dto.payment.yookassa.CreatePaymentRequest;
import ru.anyforms.dto.payment.yookassa.PaymentConfirmation;
import ru.anyforms.dto.payment.yookassa.PaymentCustomer;
import ru.anyforms.dto.payment.yookassa.PaymentItem;
import ru.anyforms.dto.payment.yookassa.PaymentReceipt;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.PurchaseService;
import ru.anyforms.service.payment.YooKassaService;
import ru.anyforms.util.MoneyUtil;

import java.util.List;

/**
 * Покупка продукта: строит запрос в Юкассу по {@link PaymentProduct}, сохраняет транзакцию
 * в статусе PENDING и возвращает ссылку на оплату.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class PurchaseServiceImpl implements PurchaseService {

    private static final String PAYMENT_MODE = "full_payment";
    private static final String PAYMENT_SUBJECT = "service";
    private static final String CONFIRMATION_REDIRECT = "redirect";
    private static final String DEFAULT_FULL_NAME = "Клиент не представился";

    private final YooKassaService yooKassaService;
    private final SaverTransaction saverTransaction;
    private final PaymentStatusConverter paymentStatusConverter;

    @Value("${payment.success.url}")
    private String paymentSuccessUrl;

    @Override
    public PaymentUrlResponse purchase(PurchaseRequest request) {
        PaymentProduct product = PaymentProduct.fromCode(request.getProductCode());

        Amount amount = Amount.builder()
                .value(MoneyUtil.kopecksToString(product.getPriceKopecks()))
                .currency(Currency.RUB.getCode())
                .build();

        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .amount(amount)
                .description("Покупка: " + product.getTitle())
                .capture(true)
                .confirmation(new PaymentConfirmation(CONFIRMATION_REDIRECT, paymentSuccessUrl))
                .receipt(buildReceipt(product, amount, request))
                .paymentMode(PAYMENT_MODE)
                .paymentSubject(PAYMENT_SUBJECT)
                .build();

        YooKassaPaymentResponse response = yooKassaService.createPayment(paymentRequest);

        if (Boolean.TRUE.equals(response.getPaid())) {
            throw new RuntimeException("Продукт уже оплачен");
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .externalPaymentId(response.getId())
                .product(product)
                .amount(MoneyUtil.stringToKopecks(response.getAmount().getValue()))
                .currency(Currency.fromCode(response.getAmount().getCurrency()))
                .description(response.getDescription())
                .email(request.getEmail())
                .status(resolveStatus(response.getStatus()))
                .build();
        saverTransaction.save(transaction);

        return new PaymentUrlResponse(
                response.getId(),
                response.getConfirmation().getConfirmationUrl(),
                response.getAmount()
        );
    }

    private PaymentTransactionStatus resolveStatus(String yooKassaStatus) {
        PaymentTransactionStatus status = paymentStatusConverter.fromYooKassa(yooKassaStatus);
        return status != null ? status : PaymentTransactionStatus.PENDING;
    }

    private PaymentReceipt buildReceipt(PaymentProduct product, Amount amount, PurchaseRequest request) {
        String fullName = (request.getFullName() == null || request.getFullName().isBlank())
                ? DEFAULT_FULL_NAME
                : request.getFullName();
        PaymentCustomer customer = new PaymentCustomer(fullName, request.getEmail());
        PaymentItem item = new PaymentItem(product.getDescription(), amount, product.getVatCode(), 1);
        return new PaymentReceipt(customer, List.of(item));
    }
}
