package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.BotRunType;

import java.util.List;

@Schema(description = "Раздел аналитики: группа цепочки (type=DRIP, groupId задан) или служебный тип запусков")
public record BotAnalyticsTypeDTO(
        @Schema(description = "Ключ раздела: group:<id> или тип") String key,
        BotRunType type,
        @Schema(description = "Группа для DRIP; null — служебный тип или запись без группы") Long groupId,
        @Schema(description = "Название группы или подпись типа") String label,
        @Schema(description = "Группа удалена, записи в журнале остались") boolean groupDeleted,
        long sent,
        long blocked,
        long failed,
        List<BotAnalyticsStepDTO> steps
) {
}
