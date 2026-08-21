package ru.anyforms.dto.amo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Тело таски на сделку и задачу в АМО о неуспешной оплате. Намеренно содержит только
 * ID транзакции — почту, ФИО и телефон раннер тянет из payment_transaction в момент исполнения.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FailedPaymentAmoTaskPayload {
    private UUID transactionId;
}
