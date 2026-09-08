package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Итоги по журналу ботов за период")
public record BotAnalyticsTotalsDTO(
        @Schema(description = "Запусков ушло в amoCRM (SUCCESS)") long sent,
        @Schema(description = "Сообщение не доставлено (MESSAGE_SEND_FAILED)") long blocked,
        @Schema(description = "Бот не запущен (FAILED)") long failed,
        @Schema(description = "Уникальных лидов, по которым были попытки") long leads
) {
}
