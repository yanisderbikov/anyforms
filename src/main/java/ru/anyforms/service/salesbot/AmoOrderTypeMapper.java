package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.OrderType;

import java.util.Optional;

/**
 * Маппинг значения поля «тип заказа» из amoCRM → {@link OrderType}.
 * <p>
 * НЕ на критическом пути основного прогона: оркестратор перебирает типы из
 * {@code order_type_funnel}, а не из поля лида. Порт оставлен для сценариев, где
 * тип нужно определить по конкретной сделке (например, при ручном запуске/диагностике).
 *
 * <p>TODO: уточнить фактические значения поля type в amoCRM и реализовать маппинг.
 * Поле уже существует, но, возможно, потребуется расширить его enum-значениями под
 * {@link OrderType} (RETAIL / RETAIL_REPEAT / CUSTOM / CUSTOM_REPEAT).
 */
public interface AmoOrderTypeMapper {

    /**
     * @param amoFieldValue сырое значение поля type из amoCRM (enum_id/enum_code/строка)
     * @return соответствующий {@link OrderType} или {@link Optional#empty()}, если не распознано
     */
    Optional<OrderType> fromAmoFieldValue(String amoFieldValue);
}
