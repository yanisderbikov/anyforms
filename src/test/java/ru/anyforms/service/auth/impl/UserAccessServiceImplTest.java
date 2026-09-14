package ru.anyforms.service.auth.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.Role;
import ru.anyforms.model.User;
import ru.anyforms.repository.UserRepository;
import ru.anyforms.service.auth.SuperAdminResolver;
import ru.anyforms.service.auth.UserAccess;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAccessServiceImplTest {

    private static final String EMAIL = "manager@anyforms.ru";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAccessServiceImpl service =
            new UserAccessServiceImpl(userRepository, new SuperAdminResolver("boss@anyforms.ru"), Duration.ofMinutes(5));

    private User manager(Role role) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setRole(role);
        return user;
    }

    @Test
    void secondLookupIsServedFromCache() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager(Role.SALES_MANAGER)));

        Optional<UserAccess> first = service.resolve(EMAIL);
        Optional<UserAccess> second = service.resolve(" Manager@AnyForms.ru ");

        assertEquals(Role.SALES_MANAGER, first.orElseThrow().role());
        assertEquals(first, second);
        verify(userRepository, times(1)).findByEmail(EMAIL);
    }

    @Test
    void missingUserIsCachedToo() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertTrue(service.resolve(EMAIL).isEmpty());
        assertTrue(service.resolve(EMAIL).isEmpty());

        verify(userRepository, times(1)).findByEmail(EMAIL);
    }

    @Test
    void evictForcesReloadSoRoleChangesApplyAtOnce() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(manager(Role.SALES_MANAGER)))
                .thenReturn(Optional.empty());

        assertEquals(Role.SALES_MANAGER, service.resolve(EMAIL).orElseThrow().role());
        service.evict(EMAIL);
        assertTrue(service.resolve(EMAIL).isEmpty());

        verify(userRepository, times(2)).findByEmail(EMAIL);
    }

    @Test
    void superAdminFlagComesFromEnv() {
        User boss = manager(Role.ADMIN);
        boss.setEmail("boss@anyforms.ru");
        when(userRepository.findByEmail("boss@anyforms.ru")).thenReturn(Optional.of(boss));

        assertTrue(service.resolve("boss@anyforms.ru").orElseThrow().superAdmin());
    }

    @Test
    void blankEmailNeverHitsDatabase() {
        assertTrue(service.resolve(null).isEmpty());
        assertTrue(service.resolve("  ").isEmpty());
        verifyNoInteractions(userRepository);
    }
}
