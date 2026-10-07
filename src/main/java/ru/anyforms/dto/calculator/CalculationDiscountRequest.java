package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Скидки заказа")
public class CalculationDiscountRequest {

    @DecimalMin(value = "0", message = "Скидка на формы — от 0%")
    @DecimalMax(value = "100", message = "Скидка на формы — не больше 100%")
    @Schema(description = "Запрошенная скидка на формы, %")
    private Double formsPercent;

    @DecimalMin(value = "0", message = "Скидка на разработку — от 0%")
    @DecimalMax(value = "100", message = "Скидка на разработку — не больше 100%")
    @Schema(description = "Скидка на разработку, % — только основатель")
    private Double developmentPercent;

    @DecimalMin(value = "0", message = "Промокод — от 0%")
    @DecimalMax(value = "100", message = "Промокод — не больше 100%")
    @Schema(description = "Промокод или акция поверх предложения, %")
    private Double promoPercent;

    @Schema(description = "Основатель разрешил маржу ниже минимума — скидка на формы не урезается")
    private Boolean allowBelowMinMargin;

    @Size(max = 2000, message = "Комментарий к скидке — до 2000 символов")
    private String comment;

    public boolean hasExceptions() {
        return (developmentPercent != null && developmentPercent > 0) || Boolean.TRUE.equals(allowBelowMinMargin);
    }
}
