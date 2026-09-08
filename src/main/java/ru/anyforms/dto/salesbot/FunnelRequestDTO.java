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
@Schema(description = "Создание/обновление воронки типа заказа (order_type_funnel)")
public class FunnelRequestDTO {

    @NotNull(message = "Тип заказа обязателен")
    private OrderType type;

    @NotNull(message = "ID воронки обязателен")
    @Positive(message = "ID воронки — положительное число")
    @Schema(description = "ID воронки в amoCRM", example = "10557858")
    private Long pipelineId;

    @NotNull(message = "ID статуса обязателен")
    @Positive(message = "ID статуса — положительное число")
    @Schema(description = "ID статуса (колонки) в amoCRM", example = "86451842")
    private Long statusId;
}
