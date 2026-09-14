package ru.anyforms.service.auth;

import ru.anyforms.model.Role;

public interface JwtTokenService {
    String createToken(String email, Role role, String name, boolean superAdmin);

    boolean isValid(String token);

    boolean isServiceToken(String token);

    String getUsername(String token);

    Role getRole(String token);
}
