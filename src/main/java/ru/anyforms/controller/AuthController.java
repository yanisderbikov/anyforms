package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.LoginResponseDTO;
import ru.anyforms.dto.auth.MeDTO;
import ru.anyforms.dto.auth.RequestLoginCodeDTO;
import ru.anyforms.dto.auth.VerifyLoginCodeDTO;
import ru.anyforms.service.auth.AuthService;
import ru.anyforms.service.auth.UserAccessService;

import java.security.Principal;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Вход в админку по коду из письма")
public class AuthController {

    private final AuthService authService;
    private final UserAccessService userAccessService;

    @Operation(summary = "Отправить код входа на почту",
            description = "Почта должна быть заведена супер-админом в /admin/users (или совпадать с ADMIN_SUPER_EMAIL). "
                    + "403 — доступа нет, 429 — код уже отправлен недавно")
    @PostMapping("/request-code")
    public ResponseEntity<Map<String, String>> requestCode(@Valid @RequestBody RequestLoginCodeDTO request) {
        authService.requestLoginCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Код отправлен на почту"));
    }

    @Operation(summary = "Подтвердить код и получить токен")
    @PostMapping("/verify-code")
    public ResponseEntity<LoginResponseDTO> verifyCode(@Valid @RequestBody VerifyLoginCodeDTO request) {
        return ResponseEntity.ok(authService.verifyLoginCode(request.getEmail(), request.getCode()));
    }

    @Operation(summary = "Кто я",
            description = "Роль, имя и флаг супер-админа из БД: фронт строит по ним меню при загрузке админки",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/me")
    public ResponseEntity<MeDTO> me(Principal principal) {
        return userAccessService.resolve(principal.getName())
                .map(a -> new MeDTO(a.email(), a.name(), a.role(), a.superAdmin()))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Доступ отозван"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Некорректное тело запроса"));
    }
}
