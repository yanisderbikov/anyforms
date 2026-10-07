package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Файл-референс расчёта (фото, эскиз, STL), лежит в S3")
public record CalculationReferenceDTO(
        @NotBlank(message = "У референса нет ключа файла")
        @Size(max = 512, message = "Слишком длинный ключ файла")
        @Schema(description = "Ключ объекта в бакете") String key,
        @Size(max = 255, message = "Слишком длинное имя файла")
        @Schema(description = "Исходное имя файла") String filename) {
}
