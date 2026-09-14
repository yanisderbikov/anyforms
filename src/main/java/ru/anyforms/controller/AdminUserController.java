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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.auth.AdminUserCreateRequestDTO;
import ru.anyforms.dto.auth.AdminUserDTO;
import ru.anyforms.dto.auth.AdminUserUpdateRequestDTO;
import ru.anyforms.service.auth.AdminUserService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin-users")
@RequiredArgsConstructor
@Tag(name = "AdminUsers", description = "Выдача доступов в админку (только супер-админ из ADMIN_SUPER_EMAIL)")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Все пользователи админки", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<List<AdminUserDTO>> list() {
        return ResponseEntity.ok(adminUserService.list());
    }

    @Operation(summary = "Выдать доступ по почте",
            description = "Почта нормализуется (trim + нижний регистр); 409 — доступ уже выдан",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<AdminUserDTO> create(@Valid @RequestBody AdminUserCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @Operation(summary = "Изменить имя и роль",
            description = "Супер-админу роль сменить нельзя",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDTO> update(@PathVariable("id") Long id,
                                               @Valid @RequestBody AdminUserUpdateRequestDTO request) {
        return ResponseEntity.ok(adminUserService.update(id, request));
    }

    @Operation(summary = "Отозвать доступ",
            description = "Действует сразу: следующий запрос с токеном этого пользователя получит 403",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        adminUserService.delete(id);
        return ResponseEntity.noContent().build();
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
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректное тело запроса; роль должна быть одной из: ADMIN, SALES_MANAGER, PROJECT_MANAGER, SHOP_OWNER"));
    }
}
