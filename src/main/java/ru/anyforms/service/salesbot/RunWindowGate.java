package ru.anyforms.service.salesbot;

import java.time.Instant;

/**
 * Решает, пора ли запускать прогон: не чаще раза в N минут. Когда именно уходят боты,
 * определяют задержки шагов ({@link BotStep#delayMinutes()}) и окно отправки группы
 * ({@link ActiveGroup#window()}).
 */
public interface RunWindowGate {

    /** {@code true}, если прогон нужно запустить сейчас; при этом момент запуска запоминается. */
    boolean tryClaim(Instant now);
}
