package ru.anyforms.util.retry;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/** Повторы в текущем потоке с паузой через sleep — для фоновых (@Async) вызовов, не для запросов пользователя. */
@Log4j2
@Component
class SleepingRetrier implements Retrier {

    /** Жёсткий потолок: больше попыток — ошибка вызывающего кода, а не повод долбить сервис. */
    static final int MAX_ALLOWED_ATTEMPTS = 10;

    @Override
    public boolean attempt(String name, int maxAttempts, Duration pause, BooleanSupplier call) {
        if (maxAttempts < 1 || maxAttempts > MAX_ALLOWED_ATTEMPTS) {
            throw new IllegalArgumentException(name + ": число попыток должно быть от 1 до "
                    + MAX_ALLOWED_ATTEMPTS + ", передано " + maxAttempts);
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (call.getAsBoolean()) {
                    return true;
                }
                log.warn("{}: попытка {} из {} не удалась", name, attempt, maxAttempts);
            } catch (Exception e) {
                log.warn("{}: попытка {} из {} упала: {}", name, attempt, maxAttempts, e.getMessage());
            }
            if (attempt < maxAttempts && !sleep(pause)) {
                return false;
            }
        }
        return false;
    }

    private static boolean sleep(Duration pause) {
        try {
            Thread.sleep(pause.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
