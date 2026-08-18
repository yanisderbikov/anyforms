package ru.anyforms.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.JwtTokenService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Кто получает какую роль: межсервисный секрет → SERVICE, пользовательский JWT → своя роль.
 * На эти роли завязаны правила доступа в WebSecurityConfig.
 */
class JwtAuthFilterTest {

    private static final String SERVICE_TOKEN = "s3cret";
    private static final String USER_JWT = "user.jwt.token";

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtTokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void doFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private List<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void serviceTokenInHeaderGivesServiceRole() throws Exception {
        when(jwtTokenService.isServiceToken(SERVICE_TOKEN)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Token", SERVICE_TOKEN);
        doFilter(request);

        assertEquals(List.of("ROLE_SERVICE"), authorities());
    }

    @Test
    void serviceTokenInBearerGivesServiceRole() throws Exception {
        when(jwtTokenService.isServiceToken(SERVICE_TOKEN)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + SERVICE_TOKEN);
        doFilter(request);

        assertEquals(List.of("ROLE_SERVICE"), authorities());
    }

    @Test
    void wrongOrMissingTokenLeavesRequestAnonymous() throws Exception {
        MockHttpServletRequest wrong = new MockHttpServletRequest();
        wrong.addHeader("X-Auth-Token", "nope");
        doFilter(wrong);
        assertNull(authorities());

        doFilter(new MockHttpServletRequest());
        assertNull(authorities());
    }

    @Test
    void userJwtKeepsItsOwnRole() throws Exception {
        when(jwtTokenService.isValid(USER_JWT)).thenReturn(true);
        when(jwtTokenService.getRole(USER_JWT)).thenReturn(Role.ADMIN);
        when(jwtTokenService.getUsername(USER_JWT)).thenReturn("admin@anyforms.ru");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + USER_JWT);
        doFilter(request);

        assertEquals(List.of("ROLE_ADMIN"), authorities());
        assertEquals("admin@anyforms.ru", SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
