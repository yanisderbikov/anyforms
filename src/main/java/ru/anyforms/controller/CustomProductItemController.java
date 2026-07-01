package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.CustomProductItemRequestDTO;
import ru.anyforms.dto.CustomProductStatusUpdateRequestDTO;
import ru.anyforms.dto.ShipGroupDTO;
import ru.anyforms.dto.ShipRequestDTO;
import ru.anyforms.service.CustomProductItemService;

import java.util.List;

@RestController
@RequestMapping("/api/custom-product-items")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "CustomProductItems", description = "Кастомные позиции под-заказа внутри сделки")
public class CustomProductItemController {

    private final CustomProductItemService service;

    @Operation(summary = "Список позиций: по order.id или все (без orderId — для трекера)")
    @GetMapping
    public List<CustomProductItemDTO> list(@RequestParam(required = false) Long orderId) {
        return orderId != null ? service.getByOrderId(orderId) : service.getAll();
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

    @Operation(summary = "Добавить файл(ы) к позиции")
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CustomProductItemDTO addFiles(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files) {
        return service.addFiles(id, files);
    }

    @Operation(summary = "Заказы с позициями, готовыми к отправке (группировка по заказу)")
    @GetMapping("/ready-to-ship")
    public List<ShipGroupDTO> readyToShip() {
        return service.getReadyToShipGroups();
    }

    @Operation(summary = "Отгрузить заказ: трекер + перевод готовых позиций в SENT")
    @PostMapping("/ship")
    public ResponseEntity<Void> ship(@Valid @RequestBody ShipRequestDTO request) {
        service.ship(request.getOrderId(), request.getTracker());
        return ResponseEntity.noContent().build();
    }
}
