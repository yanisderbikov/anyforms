package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Заменить бота на шаге цепочки; тип и позиция не меняются (порядок — через move)")
public class BotStepUpdateRequestDTO {

    @NotNull(message = "ID бота обязателен")
    @Positive(message = "ID бота — положительное число")
    @Schema(description = "ID SalesBot в amoCRM", example = "23489")
    private Long botId;

    @NotNull(message = "Задержка обязательна")
    @Min(value = 0, message = "Задержка не может быть отрицательной")
    @Max(value = 525600, message = "Задержка не больше года")
    @Schema(description = "Не раньше чем через N минут после предыдущего шага (для шага 1 — после попадания в статус)", example = "1440")
    private Integer delayMinutes;
}
