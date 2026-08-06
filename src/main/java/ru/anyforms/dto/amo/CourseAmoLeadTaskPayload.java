package ru.anyforms.dto.amo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Тело таски на закрытие сделки в АМО после покупки курса. Намеренно содержит только
 * ID транзакции — почту, телефон и сумму раннер тянет из payment_transaction в момент исполнения.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CourseAmoLeadTaskPayload {
    private UUID transactionId;
}
