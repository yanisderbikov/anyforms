package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос AI-подсказки технических параметров для варианта позиции")
public class AiSuggestionRequest {

    @NotNull(message = "Нет позиции для подсказки")
    @Valid
    private CalculationPositionRequest position;

    @NotNull(message = "Не выбран вариант формы")
    @Min(value = 0, message = "Некорректный вариант формы")
    private Integer variantIndex;

    @Size(max = 4000, message = "Описание — до 4000 символов")
    @Schema(description = "Что рассказал клиент об изделии")
    private String description;
}
