package ru.anyforms.service;

import ru.anyforms.model.AmoWebhook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebhookProcessingService {
    private final WebhookParserService webhookParserService;
    private final LeadIdExtractionService leadIdExtractionService;
    private final LeadProcessingService leadProcessingService;

    public void processFormDataWebhook(String formData) {
        try {
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formData);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);
            
            if (leads != null) {
                // Extract lead IDs from "add" events
                List<Long> addLeadIds = leadIdExtractionService.extractLeadIdsFromFormDataAdd(leads);
                for (Long leadId : addLeadIds) {
                    leadProcessingService.processLead(leadId);
                }
                
                // Extract lead IDs from other event types (status, mail_in, etc.)
                List<Long> eventLeadIds = leadIdExtractionService.extractLeadIdsFromFormDataEvents(leads);
                for (Long leadId : eventLeadIds) {
                    leadProcessingService.processLead(leadId);
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
        List<Long> leadIds = leadIdExtractionService.extractLeadIdsFromWebhook(webhook);
        for (Long leadId : leadIds) {
            leadProcessingService.processLead(leadId);
        }
    }
}
