package ru.anyforms.dto.calculator;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Сохранённый расчёт целиком: что ввели и что получилось")
public record OrderCalculationDTO(
        OrderCalculationListItemDTO entry,
        @Schema(description = "Входные данные расчёта (OrderCalculationRequest)") JsonNode request,
        @Schema(description = "Результат на момент сохранения (OrderCalculationResult)") JsonNode result,
        @Schema(description = "Ключ референса → временная ссылка на просмотр") Map<String, String> referenceUrls) {
}
