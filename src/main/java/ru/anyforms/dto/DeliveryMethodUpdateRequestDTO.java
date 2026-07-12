package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.anyforms.model.DeliveryMethod;

/** Смена способа получения заказа. */
@Data
@Schema(description = "Смена способа получения заказа")
public class DeliveryMethodUpdateRequestDTO {

    @NotNull
    @Schema(description = "Способ получения заказа", example = "PICKUP")
    private DeliveryMethod deliveryMethod;
}
