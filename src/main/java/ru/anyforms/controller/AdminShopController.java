package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.PresignUploadRequestDTO;
import ru.anyforms.dto.marketplace.AdminShopCreateRequestDTO;
import ru.anyforms.dto.marketplace.AdminShopDTO;
import ru.anyforms.dto.marketplace.AdminShopUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ShopAssetUploadDTO;
import ru.anyforms.service.product.ShopService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/shop/admin")
@Tag(name = "AdminShops", description = "Управление магазинами витрины из админки")
public class AdminShopController {

    private final ShopService shopService;

    @Operation(summary = "Все магазины, включая выключенные", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<List<AdminShopDTO>> list() {
        return ResponseEntity.ok(shopService.getAllShopsForAdmin());
    }

    @Operation(summary = "Создать магазин",
            description = "Витрина открывается на /shop/{slug} сразу после создания; 409 — slug занят",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<AdminShopDTO> create(@Valid @RequestBody AdminShopCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shopService.create(request));
    }

    @Operation(summary = "Изменить магазин",
            description = "Slug не меняется; anyforms выключить нельзя",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}")
    public ResponseEntity<AdminShopDTO> update(@PathVariable("id") UUID id,
                                               @Valid @RequestBody AdminShopUpdateRequestDTO request) {
        return ResponseEntity.ok(shopService.update(id, request));
    }

    @Operation(summary = "Presigned URL для логотипа, фото или видео магазина",
            description = "Файл уходит из браузера сразу в S3 (PUT по uploadUrl); publicUrl сохраняется через PUT /{id}",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/assets/presign")
    public ResponseEntity<ShopAssetUploadDTO> presignAsset(@PathVariable("id") UUID id,
                                                           @Valid @RequestBody PresignUploadRequestDTO request) {
        return ResponseEntity.ok(shopService.presignAssetUpload(id, request.getFilename(), request.getContentType()));
    }

    @Operation(summary = "Presigned URL для файла магазина, который ещё не создан",
            description = "Редактор нового магазина загружает логотип и фото до создания; файл ляжет в shop/{slug}/branding",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/assets/presign")
    public ResponseEntity<ShopAssetUploadDTO> presignNewShopAsset(@RequestParam("slug") String slug,
                                                                  @Valid @RequestBody PresignUploadRequestDTO request) {
        return ResponseEntity.ok(shopService.presignNewShopAssetUpload(slug, request.getFilename(), request.getContentType()));
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
}
