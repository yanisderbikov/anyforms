package ru.anyforms.util.retry;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Повтор произвольного вызова: {@code retrier.attempt("…", () -> gateway.call(…))}.
 * Попытка неудачна, если вызов вернул false или бросил исключение.
 */
public interface Retrier {

    /** По умолчанию: 3 попытки с паузой 2 секунды. */
    int MAX_ATTEMPTS = 3;
    Duration RETRY_PAUSE = Duration.ofSeconds(2);

    /**
     * Вызывает {@code call}, пока он не вернёт true или не кончатся попытки; между попытками — {@code pause}.
     *
     * @param name для логов: что именно повторяем
     * @return true — вызов в итоге удался, false — все попытки неудачны (или поток прерван)
     */
    boolean attempt(String name, int maxAttempts, Duration pause, BooleanSupplier call);

    /** То же с попытками и паузой по умолчанию ({@link #MAX_ATTEMPTS}, {@link #RETRY_PAUSE}). */
    default boolean attempt(String name, BooleanSupplier call) {
        return attempt(name, MAX_ATTEMPTS, RETRY_PAUSE, call);
    }
}
