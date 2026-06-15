package ru.anyforms.service.salesbot;

/**
 * Запуск SalesBot для лида (запрос №2 к amoCRM). Fire-and-forget: колбэка нет,
 * ретраев нет — если запрос ушёл успешно, считаем бота отправленным.
 */
public interface SalesbotTrigger {

    /**
     * Запускает бота {@code botId} на сделке {@code leadId}.
     *
     * @return {@code true}, если запрос на запуск ушёл успешно; {@code false} при ошибке.
     */
    boolean run(Long leadId, Long botId);
}
