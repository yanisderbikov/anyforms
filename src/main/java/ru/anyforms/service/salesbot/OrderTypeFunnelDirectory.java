package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.OrderType;

import java.util.List;
import java.util.Optional;

/**
 * Источник маппинга {@link OrderType} → {@link FunnelTarget} (таблица {@code order_type_funnel}).
 * Маленький порт (ISP): оркестратор зависит только от него, не зная про БД.
 */
public interface OrderTypeFunnelDirectory {

    /** Все типы, для которых настроена воронка/статус (т.е. которые надо обрабатывать в прогоне). */
    List<OrderType> configuredTypes();

    /** Целевая воронка/статус для типа, либо {@link Optional#empty()} если маппинг не задан. */
    Optional<FunnelTarget> targetFor(OrderType type);
}
