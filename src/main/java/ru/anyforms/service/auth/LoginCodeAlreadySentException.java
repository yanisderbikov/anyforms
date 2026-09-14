package ru.anyforms.service.auth;

import lombok.Getter;

@Getter
public class LoginCodeAlreadySentException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginCodeAlreadySentException(long retryAfterSeconds) {
        super("Код уже отправлен. Новый можно запросить через " + retryAfterSeconds + " сек.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
