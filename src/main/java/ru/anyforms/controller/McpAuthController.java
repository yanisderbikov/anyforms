package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.LoginResponseDTO;
import ru.anyforms.dto.auth.RequestLoginCodeDTO;
import ru.anyforms.dto.auth.VerifyLoginCodeDTO;
import ru.anyforms.service.auth.AuthService;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mcp/auth")
@RequiredArgsConstructor
@Tag(name = "MCP", description = "API заказов под заказ для MCP")
public class McpAuthController {

    private final AuthService authService;

    @Operation(summary = "Отправить код входа на почту",
            description = "Письмо уходит только администраторам. Ответ одинаковый для любой почты")
    @PostMapping("/request-code")
    public ResponseEntity<Map<String, String>> requestCode(@Valid @RequestBody RequestLoginCodeDTO request) {
        authService.requestMcpLoginCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Если у этой почты есть доступ, код отправлен"));
    }

    @Operation(summary = "Подтвердить код и получить MCP-токен",
            description = "Токен пускает только на GET /api/mcp/** и MCP-сервер POST /api/mcp/server, живёт auth.mcp-token.ttl-days дней. "
                    + "Выдаётся только роли ADMIN; смена роли или отзыв пользователя в /admin/users сразу гасит токен")
    @PostMapping("/verify-code")
    public ResponseEntity<LoginResponseDTO> verifyCode(@Valid @RequestBody VerifyLoginCodeDTO request) {
        return ResponseEntity.ok(authService.verifyMcpLoginCode(request.getEmail(), request.getCode()));
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
