package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.payment.tinkoff.TinkoffInitRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffReceipt;
import ru.anyforms.dto.payment.tinkoff.TinkoffReceiptItem;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.service.payment.PaymentStatusConverter;

import java.util.List;

/**
 * Общие кирпичики для платежей через Т-Кассу: настройки чека, сборка Init-запроса и позиций чека,
 * маппинг статуса. Используется покупкой обучения, корзиной маркетплейса и ручными счетами,
 * чтобы налоговые настройки и лимиты API жили в одном месте.
 */
@Component
@RequiredArgsConstructor
class TinkoffPaymentSupport {

    static final String PROVIDER_NAME = "tinkoff";
    /** Признак способа расчёта в чеке: полная оплата. */
    static final String PAYMENT_MODE = "full_payment";
    /** O — одностадийный платёж (списание сразу). */
    static final String PAY_TYPE_SINGLE_STAGE = "O";
    /** Лимит Т-Кассы на длину названия позиции чека. */
    static final int ITEM_NAME_MAX_LENGTH = 128;
    /** Лимит Т-Кассы на длину Description в Init. */
    static final int DESCRIPTION_MAX_LENGTH = 250;

    private final PaymentStatusConverter paymentStatusConverter;

    @Value("${payment.tinkoff.taxation}")
    private String taxation;

    @Value("${payment.tinkoff.tax}")
    private String tax;

    @Value("${payment.tinkoff.notification-url}")
    private String notificationUrl;

    /** Init-запрос с уже заполненными типом платежа и URL нотификаций; остальное дозаполняет вызывающий. */
    TinkoffInitRequest.TinkoffInitRequestBuilder initRequest(long amountKopecks, String orderId, String description) {
        return TinkoffInitRequest.builder()
                .amount(amountKopecks)
                .orderId(orderId)
                .description(truncate(description, DESCRIPTION_MAX_LENGTH))
                .payType(PAY_TYPE_SINGLE_STAGE)
                .notificationURL(blankToNull(notificationUrl));
    }

    /** Позиция чека по единице товара/услуги; paymentObject — commodity для товара, service для услуги. */
    TinkoffReceiptItem receiptItem(String name, long unitKopecks, int quantity, String paymentObject) {
        return TinkoffReceiptItem.builder()
                .name(truncate(name, ITEM_NAME_MAX_LENGTH))
                .price(unitKopecks)
                .quantity(quantity)
                .amount(unitKopecks * quantity)
                .tax(tax)
                .paymentMethod(PAYMENT_MODE)
                .paymentObject(paymentObject)
                .build();
    }

    TinkoffReceipt receipt(String email, String phone, List<TinkoffReceiptItem> items) {
        return TinkoffReceipt.builder()
                .email(blankToNull(email))
                .phone(blankToNull(phone))
                .taxation(taxation)
                .items(items)
                .build();
    }

    PaymentTransactionStatus resolveStatus(String tinkoffStatus) {
        PaymentTransactionStatus status = paymentStatusConverter.fromTinkoff(tinkoffStatus);
        return status != null ? status : PaymentTransactionStatus.PENDING;
    }

    static String appendParam(String url, String name, String value) {
        return url + (url.contains("?") ? "&" : "?") + name + "=" + value;
    }

    static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
