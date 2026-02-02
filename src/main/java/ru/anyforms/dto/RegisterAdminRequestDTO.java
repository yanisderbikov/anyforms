package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Запрос на регистрацию админа")
public class RegisterAdminRequestDTO {
    @NotBlank
    @Size(min = 1, max = 255)
    @Schema(description = "Логин", required = true)
    private String username;

    @NotBlank
    @Size(min = 4)
    @Schema(description = "Пароль", required = true)
    private String password;
}
