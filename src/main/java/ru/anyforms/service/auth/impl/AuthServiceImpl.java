package ru.anyforms.service.auth.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.LoginResponseDTO;
import ru.anyforms.model.Role;
import ru.anyforms.model.User;
import ru.anyforms.repository.UserRepository;
import ru.anyforms.service.auth.AuthService;
import ru.anyforms.service.auth.JwtTokenService;
import ru.anyforms.service.auth.LoginCodeAlreadySentException;
import ru.anyforms.service.auth.SuperAdminResolver;
import ru.anyforms.service.auth.UserAccessService;
import ru.anyforms.service.email.EmailService;
import ru.anyforms.service.email.EmailTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
class AuthServiceImpl implements AuthService {

    private static final String EMAIL_SUBJECT = "Код для входа в админку anyforms";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final SuperAdminResolver superAdminResolver;
    private final EmailService emailService;
    private final UserAccessService userAccessService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec codeHmacKey;
    private final Duration codeTtl;
    private final Duration resendInterval;
    private final int maxAttempts;

    @Autowired
    AuthServiceImpl(UserRepository userRepository,
                    JwtTokenService jwtTokenService,
                    SuperAdminResolver superAdminResolver,
                    EmailService emailService,
                    UserAccessService userAccessService,
                    @Value("${auth.login-code.secret}") String codeSecret,
                    @Value("${auth.login-code.ttl-minutes}") long ttlMinutes,
                    @Value("${auth.login-code.resend-seconds}") long resendSeconds,
                    @Value("${auth.login-code.max-attempts}") int maxAttempts) {
        this(userRepository, jwtTokenService, superAdminResolver, emailService, userAccessService, Clock.systemUTC(),
                codeSecret, ttlMinutes, resendSeconds, maxAttempts);
    }

    AuthServiceImpl(UserRepository userRepository,
                    JwtTokenService jwtTokenService,
                    SuperAdminResolver superAdminResolver,
                    EmailService emailService,
                    UserAccessService userAccessService,
                    Clock clock,
                    String codeSecret,
                    long ttlMinutes,
                    long resendSeconds,
                    int maxAttempts) {
        if (codeSecret == null || codeSecret.isBlank()) {
            throw new IllegalStateException("AUTH_LOGIN_CODE_SECRET не задан: без него коды входа хранятся небезопасно");
        }
        this.codeHmacKey = new SecretKeySpec(codeSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.superAdminResolver = superAdminResolver;
        this.emailService = emailService;
        this.userAccessService = userAccessService;
        this.clock = clock;
        this.codeTtl = Duration.ofMinutes(ttlMinutes);
        this.resendInterval = Duration.ofSeconds(resendSeconds);
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional
    public void requestLoginCode(String rawEmail) {
        String email = SuperAdminResolver.normalize(rawEmail);
        User user = userRepository.findByEmail(email)
                .or(() -> createSuperAdminIfMatches(email))
                .filter(u -> u.getRole().isAdminPanelRole())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Для этой почты доступ в админку не выдан"));

        Instant now = clock.instant();
        if (user.getLoginCodeSentAt() != null
                && user.getLoginCodeSentAt().plus(resendInterval).isAfter(now)) {
            long waitSeconds = Duration.between(now, user.getLoginCodeSentAt().plus(resendInterval)).toSeconds() + 1;
            throw new LoginCodeAlreadySentException(waitSeconds);
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        user.setLoginCodeHash(hash(code));
        user.setLoginCodeExpiresAt(now.plus(codeTtl));
        user.setLoginCodeSentAt(now);
        user.setLoginCodeAttempts(0);
        userRepository.save(user);

        try {
            emailService.sendEmail(user.getEmail(), EMAIL_SUBJECT,
                    EmailTemplate.getLoginCodeEmail(code, codeTtl.toMinutes()));
        } catch (RuntimeException e) {
            log.error("Не удалось отправить код входа пользователю id={}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не удалось отправить письмо. Попробуйте ещё раз через минуту");
        }
        log.info("Код входа отправлен пользователю id={}", user.getId());
    }

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponseDTO verifyLoginCode(String rawEmail, String code) {
        String email = SuperAdminResolver.normalize(rawEmail);
        User user = userRepository.findByEmail(email)
                .filter(u -> u.getRole().isAdminPanelRole())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Для этой почты доступ в админку не выдан"));

        Instant now = clock.instant();
        if (user.getLoginCodeHash() == null
                || user.getLoginCodeExpiresAt() == null
                || !user.getLoginCodeExpiresAt().isAfter(now)) {
            clearCode(user);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Код устарел. Запросите новый");
        }
        if (user.getLoginCodeAttempts() >= maxAttempts) {
            clearCode(user);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Слишком много неверных попыток. Запросите новый код");
        }
        if (!MessageDigest.isEqual(
                hash(code).getBytes(StandardCharsets.UTF_8),
                user.getLoginCodeHash().getBytes(StandardCharsets.UTF_8))) {
            user.setLoginCodeAttempts(user.getLoginCodeAttempts() + 1);
            userRepository.save(user);
            int left = maxAttempts - user.getLoginCodeAttempts();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    left > 0 ? "Неверный код. Осталось попыток: " + left
                             : "Неверный код. Запросите новый");
        }

        clearCode(user);
        user.setLastLoginAt(now);
        userRepository.save(user);
        userAccessService.evict(user.getEmail());
        log.info("Вход в админку: пользователь id={}, роль {}", user.getId(), user.getRole());

        String token = jwtTokenService.createToken(user.getEmail(), user.getRole(), user.getName(),
                superAdminResolver.isSuperAdmin(user.getEmail()));
        return new LoginResponseDTO(token);
    }

    private Optional<User> createSuperAdminIfMatches(String email) {
        if (!superAdminResolver.isSuperAdmin(email)) {
            return Optional.empty();
        }
        User user = new User();
        user.setEmail(email);
        user.setName(email.substring(0, email.indexOf('@')));
        user.setRole(Role.ADMIN);
        log.info("Супер-админ входит впервые: заводим запись в users");
        return Optional.of(userRepository.save(user));
    }

    private void clearCode(User user) {
        user.setLoginCodeHash(null);
        user.setLoginCodeExpiresAt(null);
        user.setLoginCodeAttempts(0);
        userRepository.save(user);
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(codeHmacKey);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
