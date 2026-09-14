package ru.anyforms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.anyforms.model.Role;

@Data
@Schema(description = "Выдача доступа в админку по почте")
public class AdminUserCreateRequestDTO {
    @NotBlank(message = "Укажите почту")
    @Email(message = "Некорректная почта")
    @Size(max = 255)
    @Schema(description = "Почта, по которой человек будет входить", required = true, example = "manager@anyforms.ru")
    private String email;

    @NotBlank(message = "Укажите имя")
    @Size(max = 255)
    @Schema(description = "Имя (для приветствия в админке)", required = true, example = "Юра")
    private String name;

    @NotNull(message = "Укажите роль: ADMIN, SALES_MANAGER или PROJECT_MANAGER")
    @Schema(description = "Роль в админке", required = true, example = "SALES_MANAGER")
    private Role role;
}
