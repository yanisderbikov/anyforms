package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Настройка дрип-кампании для одного типа заказа: где искать лидов и какие боты уходят по порядку")
public record SalesbotTypeConfigDTO(
        @Schema(description = "Значение enum OrderType") String type,
        String label,
        String description,
        @Schema(description = "Участвует ли тип в дрип-кампании") boolean drip,
        @Schema(description = "Воронка/статус; null — не задана, тип не обрабатывается") FunnelDTO funnel,
        @Schema(description = "Цепочка ботов по возрастанию позиции") List<BotStepDTO> steps
) {
}
