package ru.anyforms.dto.calculator.ai;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Референс для AI: имя файла и временная ссылка на скачивание")
public record CalculationAiReference(
        String filename,
        @Schema(description = "Presigned GET URL, живёт ограниченное время") String url) {
}
