package ru.anyforms.service.auth;

import ru.anyforms.model.Role;

public interface JwtTokenService {
    String createToken(String username, Role role);

    boolean isValid(String token);

    String getUsername(String token);

    Role getRole(String token);
}
