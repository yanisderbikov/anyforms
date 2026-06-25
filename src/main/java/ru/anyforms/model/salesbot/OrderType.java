package ru.anyforms.model.salesbot;

/**
 * Тип заказа. У каждого типа своя пара (pipeline_id, status_id) в amoCRM
 * (см. таблицу {@code order_type_funnel}) и свой список ботов
 * (см. таблицу {@code bot_sequence}).
 */
public enum OrderType {
    /** Розница (первичная продажа). */
    RETAIL,
    /** Розница, повторные продажи. */
    RETAIL_REPEAT,
    /** Под заказ (первичная). */
    CUSTOM,
    /** Под заказ, повторные продажи. */
    CUSTOM_REPEAT,
    /** Ручной массовый запуск бота по воронке/статусу (вне цепочки дрип-кампании). */
    MANUAL
}
