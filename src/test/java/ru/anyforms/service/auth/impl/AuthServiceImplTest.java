package ru.anyforms.service.auth.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.model.Role;
import ru.anyforms.model.User;
import ru.anyforms.repository.UserRepository;
import ru.anyforms.service.auth.JwtTokenService;
import ru.anyforms.service.auth.LoginCodeAlreadySentException;
import ru.anyforms.service.auth.SuperAdminResolver;
import ru.anyforms.service.auth.UserAccessService;
import ru.anyforms.service.email.EmailService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private static final String SUPER = "boss@anyforms.ru";
    private static final String MANAGER = "manager@anyforms.ru";
    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final Pattern CODE_IN_LETTER = Pattern.compile("(\\d{6})</td>");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private MutableClock clock;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new AuthServiceImpl(userRepository, jwtTokenService, new SuperAdminResolver(SUPER),
                emailService, userAccessService, clock, "test-code-secret", 10, 60, 5);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenService.createToken(anyString(), any(), any(), anyBoolean())).thenReturn("jwt");
    }

    private User manager() {
        User user = new User();
        user.setId(7L);
        user.setEmail(MANAGER);
        user.setName("Юра");
        user.setRole(Role.SALES_MANAGER);
        return user;
    }

    private String sentCode() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendEmail(eq(MANAGER), anyString(), body.capture());
        Matcher m = CODE_IN_LETTER.matcher(body.getValue());
        assertTrue(m.find(), "в письме нет шестизначного кода");
        return m.group(1);
    }

    @Test
    void storedHashIsKeyedSoPlainSha256OfCodeDoesNotMatch() throws Exception {
        User user = manager();
        when(userRepository.findByEmail(MANAGER)).thenReturn(Optional.of(user));

        service.requestLoginCode(MANAGER);
        String code = sentCode();

        byte[] sha = java.security.MessageDigest.getInstance("SHA-256").digest(code.getBytes());
        assertNotEquals(java.util.HexFormat.of().formatHex(sha), user.getLoginCodeHash());
        assertEquals(64, user.getLoginCodeHash().length());
    }

    @Test
    void unknownEmailIsForbiddenAndGetsNoLetter() {
        when(userRepository.findByEmail("stranger@x.ru")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.requestLoginCode("Stranger@X.ru "));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        verifyNoInteractions(emailService);
    }

    @Test
    void superAdminIsCreatedOnFirstLogin() {
        when(userRepository.findByEmail(SUPER)).thenReturn(Optional.empty());

        service.requestLoginCode(SUPER.toUpperCase());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(saved.capture());
        User created = saved.getAllValues().get(0);
        assertEquals(SUPER, created.getEmail());
        assertEquals(Role.ADMIN, created.getRole());
        assertEquals("boss", created.getName());
        verify(emailService).sendEmail(eq(SUPER), anyString(), anyString());
    }

    @Test
    void codeIsStoredHashedAndVerifiesOnce() {
        User user = manager();
        when(userRepository.findByEmail(MANAGER)).thenReturn(Optional.of(user));

        service.requestLoginCode(MANAGER);
        String code = sentCode();
        assertNotEquals(code, user.getLoginCodeHash());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), user.getLoginCodeExpiresAt());

        assertEquals("jwt", service.verifyLoginCode(MANAGER, code).getToken());
        verify(jwtTokenService).createToken(MANAGER, Role.SALES_MANAGER, "Юра", false);
        assertNull(user.getLoginCodeHash());
        assertEquals(NOW, user.getLastLoginAt());

        ResponseStatusException again = assertThrows(ResponseStatusException.class,
                () -> service.verifyLoginCode(MANAGER, code));
        assertEquals(HttpStatus.BAD_REQUEST, again.getStatusCode());
    }

    @Test
    void superAdminFlagGoesIntoToken() {
        User user = manager();
        user.setEmail(SUPER);
        user.setRole(Role.ADMIN);
        when(userRepository.findByEmail(SUPER)).thenReturn(Optional.of(user));

        service.requestLoginCode(SUPER);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq(SUPER), anyString(), body.capture());
        Matcher m = CODE_IN_LETTER.matcher(body.getValue());
        assertTrue(m.find());

        service.verifyLoginCode(SUPER, m.group(1));

        verify(jwtTokenService).createToken(SUPER, Role.ADMIN, "Юра", true);
    }

    @Test
    void wrongCodeCountsAttemptsAndLocksAfterLimit() {
        User user = manager();
        when(userRepository.findByEmail(MANAGER)).thenReturn(Optional.of(user));
        service.requestLoginCode(MANAGER);
        String code = sentCode();
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThrows(ResponseStatusException.class, () -> service.verifyLoginCode(MANAGER, wrong));
        }
        assertEquals(5, user.getLoginCodeAttempts());

        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
                () -> service.verifyLoginCode(MANAGER, code));
        assertEquals(HttpStatus.BAD_REQUEST, locked.getStatusCode());
        assertNull(user.getLoginCodeHash());
    }

    @Test
    void expiredCodeIsRejected() {
        User user = manager();
        when(userRepository.findByEmail(MANAGER)).thenReturn(Optional.of(user));
        service.requestLoginCode(MANAGER);
        String code = sentCode();

        clock.advance(Duration.ofMinutes(10));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.verifyLoginCode(MANAGER, code));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(jwtTokenService, never()).createToken(anyString(), any(), any(), anyBoolean());
    }

    @Test
    void resendIsThrottledForAMinute() {
        User user = manager();
        when(userRepository.findByEmail(MANAGER)).thenReturn(Optional.of(user));
        service.requestLoginCode(MANAGER);

        clock.advance(Duration.ofSeconds(30));
        LoginCodeAlreadySentException e = assertThrows(LoginCodeAlreadySentException.class,
                () -> service.requestLoginCode(MANAGER));
        assertEquals(31, e.getRetryAfterSeconds());

        clock.advance(Duration.ofSeconds(31));
        service.requestLoginCode(MANAGER);
        verify(emailService, times(2)).sendEmail(eq(MANAGER), anyString(), anyString());
    }

    @Test
    void mailFailureBecomesBadGateway() {
        User user = manager();
        when(userRepository.findByEmail(MANAGER)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("NotiSend down")).when(emailService).sendEmail(anyString(), anyString(), anyString());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.requestLoginCode(MANAGER));
        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
