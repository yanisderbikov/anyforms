package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Превью ручного запуска: скольким лидам уйдёт бот")
public record ManualRunPreviewDTO(
        @Schema(description = "Лидов в статусе (с учётом тега)") int total,
        @Schema(description = "Из них уже получали этого бота — будут пропущены") int alreadySent,
        @Schema(description = "Получат бота") int toSend,
        String pipelineName,
        String statusName,
        String botName
) {
}
