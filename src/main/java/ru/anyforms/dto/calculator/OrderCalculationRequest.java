package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Расчёт заказа: позиции складываются, варианты внутри позиции — альтернативы для КП")
public class OrderCalculationRequest {

    @Size(max = 255, message = "Слишком длинное имя клиента")
    private String client;

    @Size(max = 2000, message = "Комментарий — до 2000 символов")
    private String comment;

    @NotEmpty(message = "Добавьте хотя бы одну позицию")
    @Size(max = 30, message = "Не больше 30 позиций в одном расчёте")
    @Valid
    private List<@NotNull(message = "Пустая позиция") CalculationPositionRequest> positions;

    @Valid
    private CalculationDiscountRequest discount;

    public boolean hasExceptions() {
        return (positions != null && positions.stream().anyMatch(p -> p != null && p.hasExceptions()))
                || (discount != null && discount.hasExceptions());
    }
}
