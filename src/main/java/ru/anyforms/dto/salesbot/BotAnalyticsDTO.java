package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Аналитика дрип-кампании: на каком шаге сообщения перестают доставляться")
public record BotAnalyticsDTO(
        @Schema(description = "Начало периода (включительно), ISO-8601 instant") String from,
        @Schema(description = "Конец периода (исключительно), ISO-8601 instant") String to,
        BotAnalyticsTotalsDTO totals,
        @Schema(description = "Типы, по которым есть записи в журнале или настроена цепочка") List<BotAnalyticsTypeDTO> types
) {
}
