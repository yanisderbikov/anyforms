package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Позиция корзины: id товара из каталога + количество. Цена берётся с сервера. */
@Data
@Schema(description = "Позиция корзины")
public class CartItemDTO {

    @NotNull
    @Schema(description = "ID товара (marketplace product)")
    private UUID productId;

    @NotNull
    @Min(1)
    @Schema(description = "Количество")
    private Integer quantity;
}
