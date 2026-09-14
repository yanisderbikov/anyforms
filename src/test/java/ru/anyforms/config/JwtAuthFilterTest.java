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
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Кто получает какую роль: межсервисный секрет → SERVICE, пользовательский JWT → роль из БД
 * (плюс SUPER_ADMIN для почты из ADMIN_SUPER_EMAIL). На эти роли завязаны правила доступа в WebSecurityConfig.
 */
class JwtAuthFilterTest {

    private static final String SERVICE_TOKEN = "s3cret";
    private static final String USER_JWT = "user.jwt.token";
    private static final String EMAIL = "admin@anyforms.ru";

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtTokenService, userAccessService);

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

    private MockHttpServletRequest userRequest() {
        when(jwtTokenService.isValid(USER_JWT)).thenReturn(true);
        when(jwtTokenService.getUsername(USER_JWT)).thenReturn(EMAIL);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + USER_JWT);
        return request;
    }

    @Test
    void serviceTokenInHeaderGivesServiceRole() throws Exception {
        when(jwtTokenService.isServiceToken(SERVICE_TOKEN)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Token", SERVICE_TOKEN);
        doFilter(request);

        assertEquals(List.of("ROLE_SERVICE"), authorities());
        verifyNoInteractions(userAccessService);
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
    void userJwtTakesRoleFromDatabase() throws Exception {
        when(userAccessService.resolve(EMAIL))
                .thenReturn(Optional.of(new UserAccess(EMAIL, "Юра", Role.SALES_MANAGER, false, null, null)));

        doFilter(userRequest());

        assertEquals(List.of("ROLE_SALES_MANAGER"), authorities());
        assertEquals(EMAIL, SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void superAdminGetsExtraAuthority() throws Exception {
        when(userAccessService.resolve(EMAIL))
                .thenReturn(Optional.of(new UserAccess(EMAIL, "Босс", Role.ADMIN, true, null, null)));

        doFilter(userRequest());

        assertEquals(List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN"), authorities());
    }

    @Test
    void revokedUserWithLiveJwtStaysAnonymous() throws Exception {
        when(userAccessService.resolve(EMAIL)).thenReturn(Optional.empty());

        doFilter(userRequest());

        assertNull(authorities());
    }
}
