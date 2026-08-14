package ru.anyforms.service.auth;

import ru.anyforms.model.Role;

public interface JwtTokenService {
    String createToken(String username, Role role, String name);

    boolean isValid(String token);

    /**
     * Общий межсервисный секрет (SERVICE_AUTH_TOKEN): им ходят telegram-pusher,
     * платформа обучения и технические ручки. Приходит в X-Auth-Token
     * или в Authorization: Bearer. Проверяется здесь же, чтобы вся авторизация
     * по токенам жила в одном месте, а правила доступа — в WebSecurityConfig.
     */
    boolean isServiceToken(String token);

    String getUsername(String token);

    Role getRole(String token);
}
