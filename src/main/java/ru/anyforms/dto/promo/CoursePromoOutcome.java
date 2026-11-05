package ru.anyforms.dto.promo;

public record CoursePromoOutcome(Status status, CoursePromoDTO promo) {

    public enum Status {
        AWAITING_PAYMENT,
        NONE,
        READY
    }

    public static CoursePromoOutcome awaitingPayment() {
        return new CoursePromoOutcome(Status.AWAITING_PAYMENT, null);
    }

    public static CoursePromoOutcome none() {
        return new CoursePromoOutcome(Status.NONE, null);
    }

    public static CoursePromoOutcome ready(CoursePromoDTO promo) {
        return new CoursePromoOutcome(Status.READY, promo);
    }
}
