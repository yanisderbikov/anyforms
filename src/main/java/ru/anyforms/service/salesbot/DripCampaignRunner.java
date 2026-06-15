package ru.anyforms.service.salesbot;

/**
 * Оркестратор одного прогона дрип-кампании.
 * <p>
 * Один прогон = один шаг каденции для ВСЕХ лидов в целевых статусах: по каждому
 * настроенному типу достаёт лидов (запрос №1), для каждого вычисляет следующего бота,
 * перепроверяет статус, запускает бота (запрос №2) и пишет результат в лог.
 * <p>
 * Зависит только от портов ({@link OrderTypeFunnelDirectory}, {@link LeadProvider},
 * {@link NextBotResolver}, {@link LeadStatusVerifier}, {@link SalesbotTrigger},
 * {@link BotExecutionRecorder}) — ничего не знает про amoCRM/БД напрямую.
 */
public interface DripCampaignRunner {

    /** Выполняет ровно один прогон каденции. Вызывается под single-flight локом. */
    void runOnce();
}
