package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import ru.anyforms.model.CustomProductStatus;

import java.time.LocalDateTime;
import java.util.List;

/** Кастомная позиция под-заказа (ответ API). */
@Data
@Schema(description = "Кастомная позиция под-заказа")
public class CustomProductItemDTO {

    private Long id;

    @Schema(description = "ID сделки (наш order.id)")
    private Long orderId;

    private String productName;
    private String description;
    private Integer quantity;

    private CustomProductStatus status;

    @Schema(description = "Человекочитаемый статус", example = "В производстве")
    private String statusDescription;

    private List<CustomProductFileDTO> files;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
