package ru.anyforms.controller;

import ru.anyforms.service.amo.AmoCrmCalculateService;
import ru.anyforms.service.amo.AmoCrmWebhookService;
import ru.anyforms.service.amo.AmoNewMessageProcessor;
import ru.anyforms.dto.amo.AmoNewMessageWebhookPayload;
import ru.anyforms.util.FormDataParser;
import ru.anyforms.util.AmoNewMessagePayloadParser;
import ru.anyforms.service.CdekWebhookService;
import ru.anyforms.util.WebhookParserService;
import ru.anyforms.util.amo.JsonLeadIdExtractionService;
import ru.anyforms.service.impl.HorseDeliveryCalculationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {
    private final AmoCrmWebhookService webhookProcessingService;
    private final CdekWebhookService cdekWebhookService;
    private final AmoCrmCalculateService amoCrmCalculateService;
    private final WebhookParserService webhookParserService;
    private final JsonLeadIdExtractionService jsonLeadIdExtractionService;
    private final HorseDeliveryCalculationService horseDeliveryCalculationService;
    private final AmoNewMessageProcessor amoNewMessageProcessor;

    public WebhookController(AmoCrmWebhookService webhookProcessingService,
                             CdekWebhookService cdekWebhookService,
                             AmoCrmCalculateService amoCrmCalculateService,
                             WebhookParserService webhookParserService,
                             JsonLeadIdExtractionService jsonLeadIdExtractionService,
                             HorseDeliveryCalculationService horseDeliveryCalculationService,
                             AmoNewMessageProcessor amoNewMessageProcessor) {
        this.webhookProcessingService = webhookProcessingService;
        this.cdekWebhookService = cdekWebhookService;
        this.amoCrmCalculateService = amoCrmCalculateService;
        this.webhookParserService = webhookParserService;
        this.jsonLeadIdExtractionService = jsonLeadIdExtractionService;
        this.horseDeliveryCalculationService = horseDeliveryCalculationService;
        this.amoNewMessageProcessor = amoNewMessageProcessor;
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
            }  else {
                return ResponseEntity.badRequest().body("No data received");
            }
            
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing webhook: " + e.getMessage());
        }
    }

    @PostMapping(value = "/amocrm/new-message", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> handleAmoCrmNewMessage(
            @RequestParam(required = false) MultiValueMap<String, String> formData,
            @RequestBody(required = false) String body) {
        try {
            String formDataString = null;
            if (formData != null && !formData.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                formData.forEach((key, values) -> {
                    values.forEach(value -> {
                        if (sb.length() > 0) sb.append("&");
                        sb.append(key).append("=").append(value);
                    });
                });
                formDataString = sb.toString();
            } else if (body != null && !body.trim().isEmpty() && !body.trim().startsWith("{")) {
                formDataString = body;
            }
            if (formDataString == null) {
                return ResponseEntity.badRequest().body("No data received");
            }
            Map<String, Object> parsed = FormDataParser.parse(formDataString);
            AmoNewMessageWebhookPayload payload = AmoNewMessagePayloadParser.parse(parsed);
            if (payload == null || payload.getMessage() == null) {
                return ResponseEntity.badRequest().body("Failed to parse webhook payload");
            }
            amoNewMessageProcessor.process(payload);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing webhook: " + e.getMessage());
        }
    }

    @PostMapping(value = "/amocrm/sync-order", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> handleAmoCrmSyncOrderWebhook(
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
                webhookProcessingService.processFormDataSyncOrderWebhook(formDataString.toString());
            }  else {
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
            List<Long> addLeadIds = jsonLeadIdExtractionService.extractLeadIdsFromFormDataAdd(leads);
            List<Long> eventLeadIds = jsonLeadIdExtractionService.extractLeadIdsFromFormDataEvents(leads);
            
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
}
