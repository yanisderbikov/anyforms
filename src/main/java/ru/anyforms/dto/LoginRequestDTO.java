package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Запрос на вход")
public class LoginRequestDTO {
    @NotBlank
    @Schema(description = "Логин", required = true)
    private String username;

    @NotBlank
    @Schema(description = "Пароль", required = true)
    private String password;
}
