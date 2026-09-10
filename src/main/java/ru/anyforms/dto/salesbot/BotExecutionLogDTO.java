package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.BotExecutionLog;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.BotRunType;

import java.util.Map;

@Schema(description = "Запись журнала запусков ботов (bot_execution_log)")
public record BotExecutionLogDTO(
        Long id,
        @Schema(description = "ID сделки в amoCRM") Long leadId,
        @Schema(description = "ID SalesBot в amoCRM") Long botId,
        @Schema(description = "Название бота из amoCRM; null — неизвестно") String botName,
        Integer position,
        BotRunType type,
        @Schema(description = "Группа цепочки для DRIP; null — служебная запись") Long groupId,
        @Schema(description = "Название группы; null — служебная запись или группа удалена") String groupName,
        BotExecutionStatus status,
        @Schema(description = "Момент попытки, ISO-8601 instant") String dateExecuted
) {
    /** Instant отдаём строкой: без явной настройки Jackson сериализует его в epoch-секунды. */
    public static BotExecutionLogDTO from(BotExecutionLog log, Map<Long, String> botNames, Map<Long, String> groupNames) {
        return new BotExecutionLogDTO(
                log.getId(),
                log.getLeadId(),
                log.getBotId(),
                botNames.get(log.getBotId()),
                log.getPosition(),
                log.getType(),
                log.getGroupId(),
                log.getGroupId() != null ? groupNames.get(log.getGroupId()) : null,
                log.getStatus(),
                log.getDateExecuted() != null ? log.getDateExecuted().toString() : null);
    }
}
