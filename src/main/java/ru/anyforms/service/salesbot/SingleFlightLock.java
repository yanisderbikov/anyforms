package ru.anyforms.service.salesbot;

/**
 * Single-flight: гарантия, что в один момент времени выполняется не более одного
 * прогона шедулера (в т.ч. при нескольких инстансах приложения).
 * <p>
 * Уровень 1 защиты от двойной отправки (уровень 2 — {@code UNIQUE(lead_id, bot_id)} в логе).
 */
public interface SingleFlightLock {

    /**
     * Пытается захватить лок и, если получилось, выполняет {@code action}; иначе — пропускает.
     *
     * @return {@code true}, если лок был захвачен и {@code action} выполнен;
     *         {@code false}, если лок уже удерживается другим прогоном (действие пропущено).
     */
    boolean runExclusively(Runnable action);
}
