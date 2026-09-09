package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.BotRunType;

@Schema(description = "Тип записи журнала ботов (справочник для фильтров админки)")
public record BotRunTypeDTO(
        @Schema(description = "Значение enum BotRunType", example = "DRIP") String value,
        String label,
        String description
) {
    public static BotRunTypeDTO from(BotRunType type) {
        return new BotRunTypeDTO(type.name(), type.getLabel(), type.getDescription());
    }
}
