package ru.anyforms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.anyforms.model.Role;

@Data
@Schema(description = "Изменение имени и роли пользователя админки")
public class AdminUserUpdateRequestDTO {
    @NotBlank(message = "Укажите имя")
    @Size(max = 255)
    @Schema(description = "Имя", required = true, example = "Юра")
    private String name;

    @NotNull(message = "Укажите роль: ADMIN, SALES_MANAGER, PROJECT_MANAGER или SHOP_OWNER")
    @Schema(description = "Роль в админке", required = true, example = "PROJECT_MANAGER")
    private Role role;

    @Size(max = 64)
    @Schema(description = "Slug магазина; обязателен для SHOP_OWNER, для остальных ролей игнорируется", example = "af_pastry")
    private String shopSlug;
}
