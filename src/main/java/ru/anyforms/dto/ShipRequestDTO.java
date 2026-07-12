package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Отгрузка заказа. Для СДЭК обязателен трекер, для самовывоза трекер не нужен. */
@Data
@Schema(description = "Отгрузка заказа")
public class ShipRequestDTO {

    @NotNull
    private Long orderId;

    @Schema(description = "Номер трекера (обязателен для СДЭК, игнорируется для самовывоза)")
    private String tracker;
}
