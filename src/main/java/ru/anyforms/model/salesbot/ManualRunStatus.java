package ru.anyforms.model.salesbot;

/** Состояние ручного массового запуска бота (см. {@code ManualRun}). */
public enum ManualRunStatus {
    /** Идёт: лиды выбираются/обрабатываются в фоне. */
    RUNNING,
    /** Завершён штатно (счётчики финальные). */
    DONE,
    /** Прерван ошибкой (например, amoCRM не отдал список лидов). */
    FAILED
}
