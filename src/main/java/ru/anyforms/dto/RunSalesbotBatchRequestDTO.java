package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Запрос на запуск SalesBot для всех лидов в заданной воронке/статусе")
public class RunSalesbotBatchRequestDTO {
    @NotNull
    @Schema(description = "ID воронки в amoCRM", required = true, example = "7654321")
    private Long pipelineId;

    @NotNull
    @Schema(description = "ID статуса (колонки) в amoCRM", required = true, example = "12345678")
    private Long statusId;

    @NotNull
    @Schema(description = "ID бота SalesBot для запуска", required = true, example = "1234")
    private Long botId;
}
