package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Создание/обновление кастомной позиции. */
@Data
@Schema(description = "Создание/обновление кастомной позиции")
public class CustomProductItemRequestDTO {

    @NotBlank
    @Schema(description = "Название продукта", example = "Форма «Сердце»")
    private String productName;

    @Schema(description = "Описание")
    private String description;

    @NotNull
    @Min(1)
    @Schema(description = "Количество", example = "5")
    private Integer quantity;
}
