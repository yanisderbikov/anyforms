package ru.anyforms.service.impl;

import ru.anyforms.model.AmoWebhook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.service.AmoCrmWebhookService;
import ru.anyforms.service.LeadProcessingService;
import ru.anyforms.service.OrderService;
import ru.anyforms.util.WebhookParserService;
import ru.anyforms.util.amo.JsonLeadIdExtractionService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class AmoCrmWebhookServiceImpl implements AmoCrmWebhookService {
    private final WebhookParserService webhookParserService;
    private final JsonLeadIdExtractionService jsonLeadIdExtraction;
    private final LeadProcessingService leadProcessingService;
    private final OrderService orderService;

    public void processFormDataWebhook(String formData) {
        try {
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formData);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);
            
            if (leads != null) {
                // Extract lead IDs from "add" events
                List<Long> addLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataAdd(leads);
                for (Long leadId : addLeadIds) {
                    leadProcessingService.addLeadToOrderAndGoogleSheet(leadId);
                }
                
                // Extract lead IDs from other event types (status, mail_in, etc.)
                List<Long> eventLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataEvents(leads);
                for (Long leadId : eventLeadIds) {
                    leadProcessingService.addLeadToOrderAndGoogleSheet(leadId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing form-data webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void processJsonWebhook(String jsonBody) {
        try {
            AmoWebhook webhook = webhookParserService.parseJsonWebhook(jsonBody);
            processWebhook(webhook);
        } catch (Exception e) {
            System.err.println("Error processing JSON webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void processWebhook(AmoWebhook webhook) {
        List<Long> leadIds = jsonLeadIdExtraction.extractLeadIdsFromWebhook(webhook);
        for (Long leadId : leadIds) {
            leadProcessingService.addLeadToOrderAndGoogleSheet(leadId);
            // Синхронизируем заказ в БД
            orderService.syncOrderFromAmoCrm(leadId);
        }
    }
}
