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
@Schema(description = "Запрос на ручное выставление счёта через Т-Кассу")
public class InvoiceCreateRequest {

    @NotBlank
    @Schema(description = "ФИО клиента", example = "Иванов Иван Иванович")
    private String name;

    /** Телефон обязателен — используется в фискальном чеке. */
    @NotBlank
    @Schema(description = "Телефон клиента", example = "+79991234567")
    private String phone;

    @Email
    @Schema(description = "Email клиента (опционально)", example = "client@mail.ru")
    private String email;

    /** Сумма в рублях строкой: "1190", "1 190", "1190,50". */
    @NotBlank
    @Schema(description = "Сумма в рублях", example = "1190,50")
    private String amount;

    @Schema(description = "Назначение платежа (опционально)", example = "3D-печать по индивидуальному заказу")
    private String description;
}
