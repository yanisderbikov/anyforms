package ru.anyforms.model.payment;

public enum PaymentTransactionStatus {
    PENDING,
    SUCCEEDED,
    /** Оплата не прошла или платёж отменён — деньги не списывались. */
    CANCELED,
    /** Деньги покупателю вернули (возврат после успешной оплаты). */
    REFUNDED,
    /** Неизвестный статус провайдера. */
    FAILED
}
