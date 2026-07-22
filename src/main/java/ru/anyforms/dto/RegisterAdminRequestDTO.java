package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.anyforms.model.Role;

@Data
@Schema(description = "Запрос на регистрацию пользователя админки")
public class RegisterAdminRequestDTO {
    @NotBlank
    @Size(min = 1, max = 255)
    @Schema(description = "Логин", required = true)
    private String username;

    @NotBlank
    @Size(min = 4)
    @Schema(description = "Пароль", required = true)
    private String password;

    @NotBlank(message = "Имя обязательно")
    @Size(min = 1, max = 255)
    @Schema(description = "Имя (для приветствия в админке)", required = true, example = "Юра")
    private String name;

    @NotNull(message = "Роль обязательна: ADMIN, SALES_MANAGER, PROJECT_MANAGER или CLIENT")
    @Schema(description = "Роль пользователя", required = true, example = "ADMIN")
    private Role role;
}
