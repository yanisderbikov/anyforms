package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Таска отправки письма со ссылкой на чек")
public record ReceiptTaskDTO(
        String email,
        String link,
        @Schema(description = "GUIDE / COURSE / COURSE_PERSONAL") String productCode,
        @Schema(description = "NEW / RUNNING / DONE / FAILED") String status,
        @Schema(description = "Текст ошибки при FAILED") String comment,
        Instant createdAt
) {
}
