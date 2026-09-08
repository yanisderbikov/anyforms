package ru.anyforms.repository;

import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.OrderType;

/**
 * Проекция агрегата по журналу: сколько записей с данным статусом у шага
 * (тип, позиция, бот). Заполняется constructor-expression в JPQL.
 */
public record BotStepStatusCount(OrderType type, Integer position, Long botId, BotExecutionStatus status, Long count) {
}
