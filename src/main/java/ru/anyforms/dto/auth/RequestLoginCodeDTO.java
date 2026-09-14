package ru.anyforms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Запрос кода для входа в админку")
public class RequestLoginCodeDTO {
    @NotBlank(message = "Укажите почту")
    @Email(message = "Некорректная почта")
    @Schema(description = "Почта пользователя админки", required = true, example = "manager@anyforms.ru")
    private String email;
}
