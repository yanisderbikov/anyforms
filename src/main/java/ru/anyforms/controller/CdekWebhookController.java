package ru.anyforms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.anyforms.service.impl.CdekWebhookSubscriptionService;

@RestController
@RequestMapping("/api/cdek")
public class CdekWebhookController {
    private final CdekWebhookSubscriptionService subscriptionService;

    public CdekWebhookController(CdekWebhookSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Создает подписку на вебхуки СДЭК для получения уведомлений об изменении статуса заказа
     */
    @PostMapping("/webhook/subscribe")
    public ResponseEntity<String> subscribe() {
        try {
            String uuid = subscriptionService.subscribeToOrderStatus();
            if (uuid != null) {
                return ResponseEntity.ok("Подписка создана успешно. UUID: " + uuid);
            } else {
                return ResponseEntity.status(500).body("Не удалось создать подписку");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Ошибка при создании подписки: " + e.getMessage());
        }
    }
}

