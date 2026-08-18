package ru.anyforms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.JwtTokenService;

import java.io.IOException;
import java.util.Collections;

/**
 * Единая точка разбора токенов: JWT пользователя и общий межсервисный секрет.
 * Фильтр только опознаёт, кто пришёл, и кладёт роль в контекст —
 * решение «пускать или нет» принимает WebSecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    /** Межсервисный токен: так ходят telegram-pusher и платформа обучения */
    private static final String SERVICE_HEADER = "X-Auth-Token";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String bearer = extractBearer(request.getHeader(AUTH_HEADER));

        // Сервисный секрет принимаем и в X-Auth-Token, и в Bearer (регистрация админа)
        if (jwtTokenService.isServiceToken(request.getHeader(SERVICE_HEADER))
                || jwtTokenService.isServiceToken(bearer)) {
            authenticate("service", Role.SERVICE);
        } else if (StringUtils.hasText(bearer) && jwtTokenService.isValid(bearer)) {
            Role role = jwtTokenService.getRole(bearer);
            if (role != null) {
                authenticate(jwtTokenService.getUsername(bearer), role);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String principal, Role role) {
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private String extractBearer(String header) {
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }
}
