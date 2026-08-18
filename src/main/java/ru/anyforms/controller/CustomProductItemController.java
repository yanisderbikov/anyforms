package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.anyforms.dto.ConfirmFilesRequestDTO;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.PresignUploadRequestDTO;
import ru.anyforms.dto.PresignUploadResponseDTO;
import ru.anyforms.dto.CustomProductItemRequestDTO;
import ru.anyforms.dto.CustomProductStatusUpdateRequestDTO;
import ru.anyforms.dto.ShipGroupDTO;
import ru.anyforms.dto.ShipRequestDTO;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.service.CustomProductItemService;

import java.util.List;

@RestController
@RequestMapping("/api/custom-product-items")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "CustomProductItems", description = "Кастомные позиции под-заказа внутри сделки")
public class CustomProductItemController {

    private final CustomProductItemService service;

    @Operation(summary = "Список позиций: по order.id, по статусу или все кроме завершённых")
    @GetMapping
    public List<CustomProductItemDTO> list(@RequestParam(required = false) Long orderId,
                                           @RequestParam(required = false) CustomProductStatus status) {
        if (orderId != null) {
            return service.getByOrderId(orderId);
        }
        return status != null ? service.getAllByStatus(status) : service.getAll();
    }

    @Operation(summary = "Уникальные значения «кто моделирует» (select с автодобавлением)")
    @GetMapping("/modelers")
    public List<String> modelers() {
        return service.getModelers();
    }

    @Operation(summary = "Позиция по id (для шеринга ссылки)")
    @GetMapping("/{id}")
    public CustomProductItemDTO get(@PathVariable Long id) {
        return service.getById(id);
    }

    @Operation(summary = "Создать позицию")
    @PostMapping
    public ResponseEntity<CustomProductItemDTO> create(@RequestParam Long orderId,
                                                       @Valid @RequestBody CustomProductItemRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(orderId, request));
    }

    @Operation(summary = "Обновить поля позиции")
    @PutMapping("/{id}")
    public CustomProductItemDTO update(@PathVariable Long id, @Valid @RequestBody CustomProductItemRequestDTO request) {
        return service.update(id, request);
    }

    @Operation(summary = "Сменить статус позиции")
    @PatchMapping("/{id}/status")
    public CustomProductItemDTO updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody CustomProductStatusUpdateRequestDTO request) {
        return service.updateStatus(id, request.getStatus());
    }

    @Operation(summary = "Удалить позицию (вместе с файлами в S3)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Presigned URL для загрузки файла позиции",
            description = "Файл уходит из браузера сразу в S3 (PUT по uploadUrl), бэкенд только подписывает. "
                    + "Загруженные ключи привязываются через POST /{id}/files/confirm. Требует CORS на бакете.")
    @PostMapping("/{id}/files/presign")
    public PresignUploadResponseDTO presignFileUpload(@PathVariable Long id,
                                                      @Valid @RequestBody PresignUploadRequestDTO request) {
        var presigned = service.presignFileUpload(id, request.getFilename(), request.getContentType());
        return new PresignUploadResponseDTO(presigned.uploadUrl(), presigned.key());
    }

    @Operation(summary = "Привязать загруженные в S3 файлы к позиции")
    @PostMapping("/{id}/files/confirm")
    public CustomProductItemDTO confirmFiles(@PathVariable Long id,
                                             @Valid @RequestBody ConfirmFilesRequestDTO request) {
        return service.confirmFiles(id, request.getFiles());
    }

    @Operation(summary = "Заказы с позициями, готовыми к отправке (группировка по заказу)")
    @GetMapping("/ready-to-ship")
    public List<ShipGroupDTO> readyToShip() {
        return service.getReadyToShipGroups();
    }

    @Operation(summary = "Заказы с отправленными позициями, ещё не доставленные (группировка по заказу)")
    @GetMapping("/in-delivery")
    public List<ShipGroupDTO> inDelivery() {
        return service.getInDeliveryGroups();
    }

    @Operation(summary = "Отгрузить заказ: трекер + перевод готовых позиций в DELIVERING")
    @PostMapping("/ship")
    public ResponseEntity<Void> ship(@Valid @RequestBody ShipRequestDTO request) {
        service.ship(request.getOrderId(), request.getTracker());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Завершить заказ: перевод доставляющихся позиций в COMPLETED")
    @PostMapping("/complete/{orderId}")
    public ResponseEntity<Void> complete(@PathVariable Long orderId) {
        service.completeOrder(orderId);
        return ResponseEntity.noContent().build();
    }
}
