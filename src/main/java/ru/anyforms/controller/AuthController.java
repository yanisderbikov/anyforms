package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Value("${telegram.pusher.token}")
    private String techToken;

    @Operation(summary = "Регистрация админа", description = "Только с техническим токеном в X-Auth-Token")
    @PostMapping("/register-admin")
    public ResponseEntity<Void> registerAdmin(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                              @Valid @RequestBody RegisterAdminRequestDTO request) {
        checkToken(token);
        authService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private void checkToken(String token) {
        if (techToken == null || techToken.isBlank()) {
            log.warn("telegram.pusher.token не настроен — регистрация админа отклонена");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tech token is not configured");
        }
        if (!techToken.equals(token)) {
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
