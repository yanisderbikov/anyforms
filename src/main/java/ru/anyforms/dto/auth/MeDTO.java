package ru.anyforms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.Role;

@Schema(description = "Текущий пользователь админки по данным БД (а не JWT)")
public record MeDTO(
        String email,
        String name,
        Role role,
        boolean superAdmin,
        @Schema(description = "Магазин владельца (только для SHOP_OWNER)") String shopSlug,
        String shopName
) {
}
