package ru.anyforms.service.payment;

/** Промокод не найден, выключен или вне срока действия. Сообщение показывается пользователю. */
public class InvalidPromoCodeException extends RuntimeException {

    public InvalidPromoCodeException(String message) {
        super(message);
    }
}
