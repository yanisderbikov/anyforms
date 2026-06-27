package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.anyforms.model.CustomProductStatus;

/** Смена статуса кастомной позиции. */
@Data
@Schema(description = "Смена статуса кастомной позиции")
public class CustomProductStatusUpdateRequestDTO {

    @NotNull
    @Schema(description = "Новый статус")
    private CustomProductStatus status;
}
