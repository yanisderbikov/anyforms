package ru.anyforms.service.salesbot;

import ru.anyforms.dto.salesbot.BotAnalyticsDTO;
import ru.anyforms.dto.salesbot.BotExecutionLogPageDTO;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.BotRunType;

import java.time.LocalDate;

/**
 * Чтение журнала {@code bot_execution_log} для админки: аналитика по шагам
 * («после какого бота сообщения перестают доставляться») и постраничный журнал.
 */
public interface BotExecutionAnalyticsService {

    /**
     * Агрегаты по шагам цепочек за период, разделы — группы и служебные типы. Даты —
     * календарные дни по Москве, обе границы включительно; {@code null} — без ограничения.
     */
    BotAnalyticsDTO analytics(LocalDate from, LocalDate to);

    /**
     * Журнал запусков, новые сверху. Любой фильтр может быть {@code null}.
     *
     * @param page номер страницы с 0
     * @param size размер страницы (ограничивается сверху)
     */
    BotExecutionLogPageDTO logs(BotRunType type, Long groupId, BotExecutionStatus status, Long leadId,
                                LocalDate from, LocalDate to, int page, int size);
}
