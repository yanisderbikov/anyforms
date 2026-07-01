package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import ru.anyforms.model.CustomProductStatus;

import java.time.Instant;
import java.util.List;

/** Кастомная позиция под-заказа (ответ API). */
@Data
@Schema(description = "Кастомная позиция под-заказа")
public class CustomProductItemDTO {

    private Long id;

    @Schema(description = "ID сделки (наш order.id)")
    private Long orderId;

    @Schema(description = "Имя клиента (из заказа)")
    private String clientName;

    @Schema(description = "ID сделки в amoCRM (из заказа), может быть null")
    private Long leadId;

    private String productName;
    private String description;
    private Integer quantity;

    @Schema(description = "Кто моделирует позицию")
    private String modeler;

    private CustomProductStatus status;

    @Schema(description = "Человекочитаемый статус", example = "В производстве")
    private String statusDescription;

    private List<CustomProductFileDTO> files;

    private Instant createdAt;
    private Instant updatedAt;
}
