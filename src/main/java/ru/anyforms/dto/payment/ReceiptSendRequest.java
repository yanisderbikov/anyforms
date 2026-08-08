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
@Schema(description = "Запрос на отправку письма со ссылкой на чек Юкассы")
public class ReceiptSendRequest {

    @NotBlank
    @Email
    @Schema(description = "Email получателя")
    private String email;

    @NotBlank
    @Schema(description = "Ссылка на чек")
    private String link;

    @Schema(description = "Код продукта, за который отправляется чек: GUIDE / COURSE / COURSE_PERSONAL")
    private String productCode;
}
