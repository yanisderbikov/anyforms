package ru.anyforms.dto.promo;

public record AfterPurchasePromoOutcome(Status status, AfterPurchasePromoDTO promo) {

    public enum Status {
        AWAITING_PAYMENT,
        NONE,
        READY
    }

    public static AfterPurchasePromoOutcome awaitingPayment() {
        return new AfterPurchasePromoOutcome(Status.AWAITING_PAYMENT, null);
    }

    public static AfterPurchasePromoOutcome none() {
        return new AfterPurchasePromoOutcome(Status.NONE, null);
    }

    public static AfterPurchasePromoOutcome ready(AfterPurchasePromoDTO promo) {
        return new AfterPurchasePromoOutcome(Status.READY, promo);
    }
}
