package ru.anyforms.repository;

import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.BotRunType;

/**
 * Проекция агрегата по журналу: сколько записей с данным статусом у шага
 * (тип записи, группа, позиция, бот). Заполняется constructor-expression в JPQL.
 */
public record BotStepStatusCount(BotRunType type, Long groupId, Integer position, Long botId,
                                 BotExecutionStatus status, Long count) {
}
