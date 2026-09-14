package ru.anyforms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Подтверждение кода из письма")
public class VerifyLoginCodeDTO {
    @NotBlank(message = "Укажите почту")
    @Email(message = "Некорректная почта")
    @Schema(description = "Почта, на которую отправлен код", required = true, example = "manager@anyforms.ru")
    private String email;

    @NotBlank(message = "Введите код из письма")
    @Pattern(regexp = "\\d{6}", message = "Код — 6 цифр")
    @Schema(description = "Шестизначный код из письма", required = true, example = "482913")
    private String code;
}
