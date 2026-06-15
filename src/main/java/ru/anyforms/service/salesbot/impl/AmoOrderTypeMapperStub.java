package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.service.salesbot.AmoOrderTypeMapper;

import java.util.Optional;

/**
 * ЗАГЛУШКА маппинга поля type из amoCRM → {@link OrderType}.
 * <p>
 * TODO: реализовать реальный маппинг, когда будут известны фактические значения
 * (enum_id / enum_code / текст) поля «тип заказа» в amoCRM. Возможно, поле потребуется
 * расширить значениями RETAIL / RETAIL_REPEAT / CUSTOM / CUSTOM_REPEAT.
 * <p>
 * Сейчас не на критическом пути: основной прогон перебирает типы из {@code order_type_funnel}.
 * Реализован «best-effort» разбор по имени enum, чтобы заглушка была безопасной.
 */
@Slf4j
@Component
class AmoOrderTypeMapperStub implements AmoOrderTypeMapper {

    @Override
    public Optional<OrderType> fromAmoFieldValue(String amoFieldValue) {
        if (amoFieldValue == null || amoFieldValue.isBlank()) {
            return Optional.empty();
        }
        // TODO: заменить на маппинг по реальным enum_id/enum_code из amoCRM.
        try {
            return Optional.of(OrderType.valueOf(amoFieldValue.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.debug("Unrecognized amo order type value: {}", amoFieldValue);
            return Optional.empty();
        }
    }
}
