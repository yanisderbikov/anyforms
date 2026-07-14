package ru.anyforms.dto.payment;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefundOrdersRequest {

    /** ID заказов (orders.id), по которым делаем полный возврат. */
    @NotEmpty(message = "Список заказов пуст")
    private List<Long> orderIds;
}
