package ru.anyforms.model.salesbot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Откуда взялась запись журнала {@code bot_execution_log}. Хранится строкой.
 * Раньше здесь были типы заказа (RETAIL, CUSTOM, …); цепочки теперь живут в группах
 * ({@link BotGroup}), а запись цепочки имеет тип {@link #DRIP} и {@code group_id}.
 */
@Getter
@RequiredArgsConstructor
public enum BotRunType {
    /** Шаг дрип-цепочки группы (см. {@code group_id}). */
    DRIP("Цепочка", "Шаг дрип-цепочки группы по расписанию"),
    /** Ручной массовый запуск бота по воронке/статусу из админки. */
    MANUAL("Ручной запуск", "Ручной массовый запуск бота по воронке/статусу, вне цепочек"),
    /** Уведомления о доставке: трекер отправлен / можно забрать / самовывоз готов. */
    DELIVERY("Доставка", "Уведомления о доставке: трекер отправлен, можно забрать, самовывоз готов");

    private final String label;
    private final String description;
}
