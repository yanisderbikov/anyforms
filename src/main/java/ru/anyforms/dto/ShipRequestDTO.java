package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Отгрузка заказа: трекер. После отгрузки готовые позиции переходят в SENT. */
@Data
@Schema(description = "Отгрузка заказа")
public class ShipRequestDTO {

    @NotNull
    private Long orderId;

    @NotBlank
    @Schema(description = "Номер трекера")
    private String tracker;
}
