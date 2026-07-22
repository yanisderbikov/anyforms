package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.LoginRequestDTO;
import ru.anyforms.dto.LoginResponseDTO;
import ru.anyforms.dto.RegisterAdminRequestDTO;
import ru.anyforms.service.auth.AuthService;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация и вход")
public class AuthController {

    private final AuthService authService;

    @Value("${service.auth.token}")
    private String serviceToken;

    @Operation(summary = "Регистрация админа",
            description = "Только с сервисным токеном (SERVICE_AUTH_TOKEN) в Authorization: Bearer",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/register-admin")
    public ResponseEntity<Void> registerAdmin(@Valid @RequestBody RegisterAdminRequestDTO request,
                                              HttpServletRequest httpRequest) {
        checkToken(extractBearerToken(httpRequest.getHeader("Authorization")));
        authService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private String extractBearerToken(String authorization) {
        String prefix = "Bearer ";
        if (authorization != null && authorization.startsWith(prefix)) {
            return authorization.substring(prefix.length());
        }
        return null;
    }

    private void checkToken(String token) {
        if (serviceToken == null || serviceToken.isBlank()) {
            log.warn("service.auth.token не настроен — регистрация админа отклонена");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "service token is not configured");
        }
        if (!serviceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid auth token");
        }
    }

    @Operation(summary = "Вход, получение токена")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        LoginResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
