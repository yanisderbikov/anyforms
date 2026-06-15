package ru.anyforms.service.salesbot;

/**
 * Перепроверка актуального статуса лида непосредственно перед запуском бота.
 * <p>
 * Нужна, чтобы закрыть гонку между запросом №1 и моментом запуска: если клиент
 * успел ответить, amoCRM переносит сделку в другой статус — тогда бота слать не нужно.
 */
public interface LeadStatusVerifier {

    /**
     * @return {@code true}, если лид прямо сейчас находится в целевой воронке и статусе.
     *         {@code false} — если вышел из статуса (или статус не удалось прочитать).
     */
    boolean isInTargetStatus(Long leadId, FunnelTarget target);
}
