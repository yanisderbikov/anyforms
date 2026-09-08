package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.OrderType;

@Schema(description = "Тип заказа дрип-кампании (справочник для админки)")
public record OrderTypeDTO(
        @Schema(description = "Значение enum OrderType", example = "RETAIL") String value,
        @Schema(description = "Короткая подпись", example = "Розница") String label,
        @Schema(description = "Пояснение") String description,
        @Schema(description = "Участвует ли в дрип-кампании (можно задать воронку и цепочку)") boolean drip
) {
    public static OrderTypeDTO from(OrderType type) {
        return new OrderTypeDTO(type.name(), type.getLabel(), type.getDescription(), type.isDrip());
    }
}
