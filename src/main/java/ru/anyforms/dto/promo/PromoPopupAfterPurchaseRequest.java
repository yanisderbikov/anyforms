package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Промокод на следующий заказ после оплаты")
public class PromoPopupAfterPurchaseRequest {

    @NotBlank(message = "Укажите номер заказа")
    @Size(max = 16)
    @Schema(description = "Публичный номер оплаченного заказа", example = "A1B2C3")
    private String orderNumber;

    @Size(max = 64)
    @Schema(description = "ID устройства из браузера")
    private String deviceId;
}
