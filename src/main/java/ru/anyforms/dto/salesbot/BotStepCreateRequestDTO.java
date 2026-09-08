package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.model.salesbot.OrderType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Добавить бота в конец цепочки типа (bot_sequence); позиция назначается автоматически")
public class BotStepCreateRequestDTO {

    @NotNull(message = "Тип заказа обязателен")
    private OrderType type;

    @NotNull(message = "ID бота обязателен")
    @Positive(message = "ID бота — положительное число")
    @Schema(description = "ID SalesBot в amoCRM", example = "23489")
    private Long botId;
}
