package ru.anyforms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.JwtTokenService;
import ru.anyforms.service.auth.UserAccessService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Единая точка разбора токенов: JWT пользователя и общий межсервисный секрет.
 * Фильтр только опознаёт, кто пришёл, и кладёт роль в контекст —
 * решение «пускать или нет» принимает WebSecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";

    private static final String AUTH_HEADER = "Authorization";
    /** Межсервисный токен: так ходят telegram-pusher и платформа обучения */
    private static final String SERVICE_HEADER = "X-Auth-Token";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserAccessService userAccessService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String bearer = extractBearer(request.getHeader(AUTH_HEADER));

        if (jwtTokenService.isServiceToken(request.getHeader(SERVICE_HEADER))
                || jwtTokenService.isServiceToken(bearer)) {
            authenticate("service", Role.SERVICE, false);
        } else if (StringUtils.hasText(bearer) && jwtTokenService.isValid(bearer)) {
            userAccessService.resolve(jwtTokenService.getUsername(bearer))
                    .ifPresent(access -> authenticate(access.email(), access.role(), access.superAdmin()));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String principal, Role role, boolean superAdmin) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (superAdmin) {
            authorities.add(new SimpleGrantedAuthority(SUPER_ADMIN_AUTHORITY));
        }
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
