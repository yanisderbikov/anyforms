package ru.anyforms.service.auth.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.Role;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceImplTest {

    private static final String EMAIL = "manager@anyforms.ru";

    private final JwtTokenServiceImpl service = new JwtTokenServiceImpl(
            "test-secret-test-secret-test-secret-42", 3600, 30, "");

    @Test
    void mcpTokenIsValidAndMarkedReadOnly() {
        String token = service.createMcpReadToken(EMAIL);

        assertTrue(service.isValid(token));
        assertTrue(service.isMcpReadToken(token));
        assertEquals(EMAIL, service.getUsername(token));
        assertNull(service.getRole(token));
    }

    @Test
    void regularTokenIsNotMcpToken() {
        String token = service.createToken(EMAIL, Role.ADMIN, "Юра", true);

        assertTrue(service.isValid(token));
        assertFalse(service.isMcpReadToken(token));
    }
}
