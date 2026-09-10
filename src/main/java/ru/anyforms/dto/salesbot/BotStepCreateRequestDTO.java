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
@Schema(description = "Добавить бота в конец цепочки группы (bot_sequence); позиция назначается автоматически")
public class BotStepCreateRequestDTO {

    @NotNull(message = "Группа обязательна")
    private Long groupId;

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
