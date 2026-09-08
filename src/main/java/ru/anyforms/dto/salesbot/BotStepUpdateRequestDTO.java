package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
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
}
