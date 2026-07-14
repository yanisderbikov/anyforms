package ru.anyforms.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefundOrderResult {

    private Long orderId;

    /** PaymentId Т-Кассы, по которому делали возврат. */
    private String externalPaymentId;

    /** Сумма возврата в копейках. */
    private Long amount;

    private boolean success;

    /** Статус Т-Кассы после возврата (REFUNDED и т.п.) либо причина отказа. */
    private String message;
}
