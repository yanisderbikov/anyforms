package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.dto.mcp.McpSalesAnalyticsDTO;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpSalesAnalytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "MCP", description = "API для MCP")
public class McpRetailController {

    private final McpCustomOrderReader reader;
    private final McpSalesAnalytics salesAnalytics;

    @Operation(summary = "Розница без трекера (оплаченные, ещё не отправленные), без телефона клиента")
    @GetMapping("/retail-orders")
    public List<OrderSummaryDTO> retailOrders() {
        return reader.retailOrdersWithoutTracker();
    }

    @Operation(summary = "Аналитика продаж за период",
            description = "Оплаченные заказы витрин: количество, молды, средний чек и сумма в рублях. "
                    + "from/to — ГГГГ-ММ-ДД включительно; без shop — по всем магазинам с разбивкой")
    @GetMapping("/analytics/sales")
    public McpSalesAnalyticsDTO sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String shop) {
        try {
            return salesAnalytics.sales(from, to, shop == null || shop.isBlank() ? null : shop.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
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
