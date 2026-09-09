package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Аналитика дрип-кампании: на каком шаге сообщения перестают доставляться")
public record BotAnalyticsDTO(
        @Schema(description = "Начало периода (включительно), ISO-8601 instant") String from,
        @Schema(description = "Конец периода (исключительно), ISO-8601 instant") String to,
        BotAnalyticsTotalsDTO totals,
        @Schema(description = "Разделы: группы (с записями или настроенной цепочкой) и служебные типы с записями") List<BotAnalyticsTypeDTO> sections
) {
}
