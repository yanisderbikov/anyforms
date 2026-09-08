package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Страница журнала запусков ботов, новые сверху")
public record BotExecutionLogPageDTO(
        List<BotExecutionLogDTO> content,
        @Schema(description = "Номер страницы, с 0") int page,
        int size,
        long totalElements,
        int totalPages
) {
}
