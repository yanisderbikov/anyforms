package ru.anyforms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.Role;

import java.time.Instant;

@Schema(description = "Пользователь админки")
public record AdminUserDTO(
        Long id,
        String email,
        String name,
        Role role,
        @Schema(description = "Супер-админ из ADMIN_SUPER_EMAIL: нельзя удалить или сменить роль") boolean superAdmin,
        Instant createdAt,
        Instant lastLoginAt
) {
}
