package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Группа дрип-кампании: название, воронка/статус amoCRM и цепочка ботов")
public record BotGroupDTO(
        Long id,
        String name,
        @Schema(description = "Выключенная группа в прогоне не участвует, настройки сохраняются") boolean enabled,
        @Schema(description = "ID воронки; null — не задана, группа не обрабатывается") Long pipelineId,
        String pipelineName,
        Long statusId,
        String statusName,
        @Schema(description = "Окно отправки по Москве, HH:mm; null — окно по умолчанию") String sendFrom,
        @Schema(description = "Конец окна отправки (исключительно), HH:mm") String sendTo,
        @Schema(description = "Цепочка ботов по возрастанию позиции") List<BotStepDTO> steps
) {
}
