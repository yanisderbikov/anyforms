package ru.anyforms.service.auth.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserNameResolverImplTest {

    private static final String EMAIL = "masha@anyforms.ru";

    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private final CurrentUserNameResolverImpl resolver = new CurrentUserNameResolverImpl(userAccessService);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(String principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void nameFromProfile() {
        login(EMAIL);
        when(userAccessService.resolve(EMAIL)).thenReturn(Optional.of(new UserAccess(EMAIL, "Маша", Role.ADMIN, false, null, null)));

        assertEquals(Optional.of("Маша"), resolver.currentUserName());
    }

    @Test
    void emailWhenNameIsBlank() {
        login(EMAIL);
        when(userAccessService.resolve(EMAIL)).thenReturn(Optional.of(new UserAccess(EMAIL, " ", Role.ADMIN, false, null, null)));

        assertEquals(Optional.of(EMAIL), resolver.currentUserName());
    }

    @Test
    void emptyForServiceTokenAndWithoutAuthentication() {
        assertEquals(Optional.empty(), resolver.currentUserName());

        login("service");
        when(userAccessService.resolve("service")).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), resolver.currentUserName());
    }
}
