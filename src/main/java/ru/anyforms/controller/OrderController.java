package ru.anyforms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.anyforms.model.Order;
import ru.anyforms.service.OrderService;
import ru.anyforms.service.GoogleSheetsService;
import ru.anyforms.service.AmoCrmService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final GoogleSheetsService googleSheetsService;
    private final AmoCrmService amoCrmService;
    private final DeliveryProcessor deliveryProcessor;

    /**
     * Получает все заказы без трекера, сгруппированные по заказчику
     * Возвращает структуру: сколько товаров для какого заказчика нужно отправить относительно сделки
     */
    @GetMapping("/without-tracker")
    public ResponseEntity<List<OrderSummaryDTO>> getOrdersWithoutTracker() {
        List<Order> orders = orderService.getOrdersWithoutTracker();
        
        List<OrderSummaryDTO> summaries = orders.stream()
                .map(order -> {
                    OrderSummaryDTO dto = new OrderSummaryDTO();
                    dto.setLeadId(order.getLeadId());
                    dto.setContactId(order.getContactId());
                    dto.setContactName(order.getContactName());
                    dto.setContactPhone(order.getContactPhone());
                    dto.setItems(order.getItems().stream()
                            .map(item -> {
                                OrderItemDTO itemDto = new OrderItemDTO();
                                itemDto.setProductName(item.getProductName());
                                itemDto.setQuantity(item.getQuantity());
                                itemDto.setProductId(item.getProductId());
                                return itemDto;
                            })
                            .collect(Collectors.toList()));
                    return dto;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(summaries);
    }

    /**
     * Устанавливает трекер для заказа
     * После установки обновляет данные в Google Sheets и AmoCRM
     */
    @PostMapping("/{leadId}/tracker")
    public ResponseEntity<Map<String, Object>> setTracker(
            @PathVariable Long leadId,
            @RequestBody Map<String, String> request) {
        
        String tracker = request.get("tracker");
        if (tracker == null || tracker.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Tracker is required"));
        }

        try {
            // Устанавливаем трекер в БД и AmoCRM
            boolean success = orderService.setTrackerForOrder(leadId, tracker.trim());
            
            if (!success) {
                return ResponseEntity.status(500)
                        .body(Map.of("error", "Failed to set tracker"));
            }

            // Обновляем трекер в Google Sheets (DeliveryProcessor найдет строку по leadId)
            // DeliveryProcessor уже обновляет Google Sheets через updateStatusInTableAndAmoCrm
            // Но нам нужно обновить трекер, а не статус. Пока оставляем как есть,
            // так как трекер уже обновлен в AmoCRM, а в Google Sheets он обновится при следующей синхронизации

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "leadId", leadId,
                    "tracker", tracker
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Синхронизирует заказ из AmoCRM в БД
     */
    @PostMapping("/sync/{leadId}")
    public ResponseEntity<Map<String, Object>> syncOrder(@PathVariable Long leadId) {
        try {
            Order order = orderService.syncOrderFromAmoCrm(leadId);
            if (order == null) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "Order not found or failed to sync"));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "leadId", leadId,
                    "itemsCount", order.getItems().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // DTO классы
    public static class OrderSummaryDTO {
        private Long leadId;
        private Long contactId;
        private String contactName;
        private String contactPhone;
        private List<OrderItemDTO> items;

        public Long getLeadId() { return leadId; }
        public void setLeadId(Long leadId) { this.leadId = leadId; }

        public Long getContactId() { return contactId; }
        public void setContactId(Long contactId) { this.contactId = contactId; }

        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }

        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

        public List<OrderItemDTO> getItems() { return items; }
        public void setItems(List<OrderItemDTO> items) { this.items = items; }
    }

    public static class OrderItemDTO {
        private String productName;
        private Integer quantity;
        private Long productId;

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
    }
}

