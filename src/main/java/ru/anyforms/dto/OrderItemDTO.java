package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Элемент заказа (товар)")
public class OrderItemDTO {
    @Schema(description = "Название товара", example = "Товар 1")
    private String productName;
    
    @Schema(description = "Количество", example = "2")
    private Integer quantity;
    
    @Schema(description = "ID товара в AmoCRM", example = "111")
    private Long productId;
}

