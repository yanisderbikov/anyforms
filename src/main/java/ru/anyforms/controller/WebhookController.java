package ru.anyforms.controller;

import ru.anyforms.service.WebhookProcessingService;
import ru.anyforms.service.CdekWebhookService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
public class WebhookController {
    private final WebhookProcessingService webhookProcessingService;
    private final CdekWebhookService cdekWebhookService;

    public WebhookController(WebhookProcessingService webhookProcessingService,
                             CdekWebhookService cdekWebhookService) {
        this.webhookProcessingService = webhookProcessingService;
        this.cdekWebhookService = cdekWebhookService;
    }

    @PostMapping(value = "/amocrm", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> handleAmoCrmWebhook(
            @RequestParam(required = false) MultiValueMap<String, String> formData,
            @RequestBody(required = false) String body) {
        try {
            if (formData != null && !formData.isEmpty()) {
                // Handle form-urlencoded data
                StringBuilder formDataString = new StringBuilder();
                formData.forEach((key, values) -> {
                    values.forEach(value -> {
                        if (formDataString.length() > 0) {
                            formDataString.append("&");
                        }
                        formDataString.append(key).append("=").append(value);
                    });
                });
                webhookProcessingService.processFormDataWebhook(formDataString.toString());
            } else if (body != null && !body.isEmpty()) {
                // Handle JSON or raw string data
                if (body.trim().startsWith("{")) {
                    // JSON format
                    webhookProcessingService.processJsonWebhook(body);
                } else {
                    // URL-encoded string
                    webhookProcessingService.processFormDataWebhook(body);
                }
            } else {
                return ResponseEntity.badRequest().body("No data received");
            }
            
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing webhook: " + e.getMessage());
        }
    }

    @PostMapping(value = "/cdek", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleCdekWebhook(@RequestBody String body) {
        try {
            if (body == null || body.isEmpty()) {
                return ResponseEntity.badRequest().body("No data received");
            }
            
            cdekWebhookService.processWebhook(body);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing webhook: " + e.getMessage());
        }
    }
}
