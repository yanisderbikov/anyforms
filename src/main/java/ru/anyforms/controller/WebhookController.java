package ru.anyforms.controller;

import ru.anyforms.service.WebhookProcessingService;
import ru.anyforms.service.CdekWebhookService;
import ru.anyforms.service.AmoCrmCalculateService;
import ru.anyforms.service.WebhookParserService;
import ru.anyforms.service.LeadIdExtractionService;
import ru.anyforms.service.HorseDeliveryCalculationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {
    private final WebhookProcessingService webhookProcessingService;
    private final CdekWebhookService cdekWebhookService;
    private final AmoCrmCalculateService amoCrmCalculateService;
    private final WebhookParserService webhookParserService;
    private final LeadIdExtractionService leadIdExtractionService;
    private final HorseDeliveryCalculationService horseDeliveryCalculationService;

    public WebhookController(WebhookProcessingService webhookProcessingService,
                             CdekWebhookService cdekWebhookService,
                             AmoCrmCalculateService amoCrmCalculateService,
                             WebhookParserService webhookParserService,
                             LeadIdExtractionService leadIdExtractionService,
                             HorseDeliveryCalculationService horseDeliveryCalculationService) {
        this.webhookProcessingService = webhookProcessingService;
        this.cdekWebhookService = cdekWebhookService;
        this.amoCrmCalculateService = amoCrmCalculateService;
        this.webhookParserService = webhookParserService;
        this.leadIdExtractionService = leadIdExtractionService;
        this.horseDeliveryCalculationService = horseDeliveryCalculationService;
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

    @PostMapping(value = "/amocrm/calculate", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> handleAmoCrmCalculate(
            @RequestParam(required = false) MultiValueMap<String, String> formData,
            @RequestBody(required = false) String body) {
        try {
            String formDataString = null;
            
            if (formData != null && !formData.isEmpty()) {
                // Handle form-urlencoded data
                StringBuilder formDataBuilder = new StringBuilder();
                formData.forEach((key, values) -> {
                    values.forEach(value -> {
                        if (formDataBuilder.length() > 0) {
                            formDataBuilder.append("&");
                        }
                        formDataBuilder.append(key).append("=").append(value);
                    });
                });
                formDataString = formDataBuilder.toString();
            } else if (body != null && !body.isEmpty()) {
                // Handle URL-encoded string (not JSON, as per user request - only formData processing)
                if (!body.trim().startsWith("{")) {
                    formDataString = body;
                } else {
                    return ResponseEntity.badRequest().body("Only form-data format is supported for calculate endpoint");
                }
            } else {
                return ResponseEntity.badRequest().body("No data received");
            }
            
            // Используем ту же логику парсинга, что и в /amocrm
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formDataString);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);
            
            if (leads == null) {
                return ResponseEntity.badRequest().body("No leads data found in webhook");
            }
            
            // Извлекаем lead IDs из всех типов событий (как в processFormDataWebhook)
            List<Long> addLeadIds = leadIdExtractionService.extractLeadIdsFromFormDataAdd(leads);
            List<Long> eventLeadIds = leadIdExtractionService.extractLeadIdsFromFormDataEvents(leads);
            
            // Объединяем все lead IDs
            List<Long> allLeadIds = new java.util.ArrayList<>();
            allLeadIds.addAll(addLeadIds);
            allLeadIds.addAll(eventLeadIds);
            
            if (allLeadIds.isEmpty()) {
                return ResponseEntity.badRequest().body("No lead IDs found in webhook");
            }
            
            // Выполняем расчеты для каждого lead ID
            int successCount = 0;
            int failCount = 0;
            for (Long leadId : allLeadIds) {
                boolean success = amoCrmCalculateService.calculateAndUpdateLead(leadId);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
            
            if (failCount == 0) {
                return ResponseEntity.ok("Calculation completed successfully for " + successCount + " lead(s)");
            } else {
                return ResponseEntity.status(500).body("Calculation completed for " + successCount + " lead(s), failed for " + failCount + " lead(s)");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing calculation: " + e.getMessage());
        }
    }

    @PostMapping(value = "/delivery", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> handleHorseDeliveryCalculation(
            @RequestParam(required = false) MultiValueMap<String, String> formData,
            @RequestBody(required = false) String body) {
        try {
            String formDataString = null;
            
            if (formData != null && !formData.isEmpty()) {
                // Handle form-urlencoded data
                StringBuilder formDataBuilder = new StringBuilder();
                formData.forEach((key, values) -> {
                    values.forEach(value -> {
                        if (formDataBuilder.length() > 0) {
                            formDataBuilder.append("&");
                        }
                        formDataBuilder.append(key).append("=").append(value);
                    });
                });
                formDataString = formDataBuilder.toString();
            } else if (body != null && !body.isEmpty()) {
                // Handle URL-encoded string (not JSON, as per user request - only formData processing)
                if (!body.trim().startsWith("{")) {
                    formDataString = body;
                } else {
                    return ResponseEntity.badRequest().body("Only form-data format is supported for horse-delivery endpoint");
                }
            } else {
                return ResponseEntity.badRequest().body("No data received");
            }
            
            // Используем ту же логику парсинга, что и в /amocrm
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formDataString);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);
            
            if (leads == null) {
                return ResponseEntity.badRequest().body("No leads data found in webhook");
            }
            
            // Извлекаем lead IDs из всех типов событий
            List<Long> addLeadIds = leadIdExtractionService.extractLeadIdsFromFormDataAdd(leads);
            List<Long> eventLeadIds = leadIdExtractionService.extractLeadIdsFromFormDataEvents(leads);
            
            // Объединяем все lead IDs
            List<Long> allLeadIds = new java.util.ArrayList<>();
            allLeadIds.addAll(addLeadIds);
            allLeadIds.addAll(eventLeadIds);
            
            if (allLeadIds.isEmpty()) {
                return ResponseEntity.badRequest().body("No lead IDs found in webhook");
            }
            
            // Выполняем расчет доставки для каждого lead ID
            int successCount = 0;
            int failCount = 0;
            int skippedCount = 0;
            for (Long leadId : allLeadIds) {
                boolean success = horseDeliveryCalculationService.calculateAndAddNote(leadId);
                if (success) {
                    successCount++;
                } else {
                    // Проверяем, была ли это ошибка или просто не подходит под условия (не лошадка)
                    // В сервисе уже есть логика проверки, поэтому считаем как fail только реальные ошибки
                    failCount++;
                }
            }
            
            if (failCount == 0) {
                return ResponseEntity.ok("Delivery calculation completed successfully for " + successCount + " lead(s)");
            } else {
                return ResponseEntity.status(500).body("Delivery calculation completed for " + successCount + " lead(s), failed for " + failCount + " lead(s)");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing horse delivery calculation: " + e.getMessage());
        }
    }
}
