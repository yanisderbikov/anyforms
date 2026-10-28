package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.CustomProductLogDTO;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.dto.ShipGroupDTO;
import ru.anyforms.dto.mcp.McpCustomOrderDTO;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.CustomProductLogService;
import ru.anyforms.service.mcp.McpCustomOrderReader;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "MCP", description = "API заказов под заказ для MCP")
public class McpCustomOrderController {

    private final McpCustomOrderReader reader;
    private final CustomProductItemService itemService;
    private final CustomProductLogService logService;

    @Operation(summary = "Заказы под заказ в работе (оплаченные, без трекера)")
    @GetMapping("/custom-orders")
    public List<OrderSummaryDTO> customOrders() {
        return reader.workableOrders();
    }

    @Operation(summary = "Заказ по нашему id со всеми его позициями под заказ")
    @GetMapping("/custom-orders/{orderId}")
    public McpCustomOrderDTO customOrder(@PathVariable Long orderId) {
        return reader.order(orderId);
    }

    @Operation(summary = "Позиции под заказ",
            description = "Без параметров — все, кроме завершённых; status — только в этом статусе (в т.ч. COMPLETED)")
    @GetMapping("/custom-items")
    public List<CustomProductItemDTO> items(@RequestParam(required = false) CustomProductStatus status) {
        return status != null ? itemService.getAllByStatus(status) : itemService.getAll();
    }

    @Operation(summary = "Позиция по id")
    @GetMapping("/custom-items/{id}")
    public CustomProductItemDTO item(@PathVariable Long id) {
        return itemService.getById(id);
    }

    @Operation(summary = "Позиция по публичному номеру (6 символов A-Z/0-9)")
    @GetMapping("/custom-items/by-public-id/{publicId}")
    public CustomProductItemDTO itemByPublicId(@PathVariable String publicId) {
        return itemService.getByPublicId(publicId);
    }

    @Operation(summary = "Лог производства позиции (от старых к новым)")
    @GetMapping("/custom-items/{id}/logs")
    public List<CustomProductLogDTO> logs(@PathVariable Long id) {
        return logService.getByItemId(id);
    }

    @Operation(summary = "Заказы с позициями, готовыми к отправке")
    @GetMapping("/custom-items/ready-to-ship")
    public List<ShipGroupDTO> readyToShip() {
        return itemService.getReadyToShipGroups();
    }

    @Operation(summary = "Заказы с отправленными позициями, ещё не доставленные")
    @GetMapping("/custom-items/in-delivery")
    public List<ShipGroupDTO> inDelivery() {
        return itemService.getInDeliveryGroups();
    }

    @Operation(summary = "Кто моделирует: уникальные значения по всем позициям")
    @GetMapping("/custom-items/modelers")
    public List<String> modelers() {
        return itemService.getModelers();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Некорректное значение параметра " + e.getName()));
    }
}
