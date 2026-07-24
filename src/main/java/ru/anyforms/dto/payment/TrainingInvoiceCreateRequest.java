package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на выставление счёта на обучение (гайд/курс) через Юкассу")
public class TrainingInvoiceCreateRequest {

    @NotBlank
    @Schema(description = "Код продукта: GUIDE / COURSE / COURSE_PERSONAL", example = "GUIDE")
    private String productCode;

    @NotBlank
    @Schema(description = "ФИО клиента", example = "Иванов Иван Иванович")
    private String fullName;

    /** Телефон обязателен — используется в фискальном чеке. */
    @NotBlank
    @Schema(description = "Телефон клиента", example = "+79991234567")
    private String phone;

    /** Email обязателен — после оплаты на него уходит письмо с доступом к продукту. */
    @NotBlank
    @Email
    @Schema(description = "Email клиента", example = "client@mail.ru")
    private String email;

    @Schema(description = "Промокод (опционально)", example = "ANY10")
    private String promoCode;
}
