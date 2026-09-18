package ru.anyforms.dto.cdek;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Стоимость и срок доставки СДЭК")
public record DeliveryCostResponseDTO(
        @Schema(description = "Стоимость доставки, руб.") BigDecimal cost,
        @Schema(description = "Минимальный срок доставки, дней") Integer periodMin,
        @Schema(description = "Максимальный срок доставки, дней") Integer periodMax,
        @Schema(description = "Код тарифа СДЭК") int tariffCode) {
}
