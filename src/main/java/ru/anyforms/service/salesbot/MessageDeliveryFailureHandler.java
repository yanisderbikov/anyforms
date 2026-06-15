package ru.anyforms.service.salesbot;

/**
 * Обработка ситуации «SalesBot не смог отправить сообщение лиду» (вебхук от amoCRM).
 * <p>
 * Переносит лид в статус «нереализовано». Как следствие лид выходит из целевого статуса
 * дрип-кампании → перестаёт попадать в выборку шедулера (запрос №1) и в перепроверку
 * {@link LeadStatusVerifier} → дальнейшие боты ему не уезжают. Отдельной «стоп-логики» не нужно.
 */
public interface MessageDeliveryFailureHandler {

    /**
     * @param leadId сделка, которой не удалось отправить сообщение
     * @return {@code true}, если лид успешно перенесён в статус «нереализовано»
     */
    boolean onSendFailed(Long leadId);
}
