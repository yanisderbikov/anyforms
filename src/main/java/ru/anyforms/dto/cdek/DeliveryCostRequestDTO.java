package ru.anyforms.dto.cdek;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import ru.anyforms.dto.payment.CartItemDTO;

import java.util.List;

@Data
@Schema(description = "Запрос расчёта стоимости доставки СДЭК до ПВЗ")
public class DeliveryCostRequestDTO {

    @NotEmpty
    @Valid
    @Schema(description = "Позиции корзины")
    private List<CartItemDTO> items;

    @NotBlank
    @Schema(description = "Код ПВЗ СДЭК получателя (из /api/cdek/pvz)")
    private String pvzCode;
}
