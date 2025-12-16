package ru.anyforms.service.impl;

import lombok.extern.log4j.Log4j2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.service.AmoCrmWebhookService;
import ru.anyforms.service.LeadAmoCrmStatusUpdater;
import ru.anyforms.service.OrderService;
import ru.anyforms.util.WebhookParserService;
import ru.anyforms.util.amo.JsonLeadIdExtractionService;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
class AmoCrmWebhookServiceImpl implements AmoCrmWebhookService {

    private final WebhookParserService webhookParserService;
    private final JsonLeadIdExtractionService jsonLeadIdExtraction;
    private final LeadAmoCrmStatusUpdater leadAmoCrmStatusUpdater;
    private final OrderService orderService;

    @Override
    public void processFormDataWebhook(String formData) {
        try {
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formData);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);
            
            if (leads != null) {
                // Extract lead IDs from "add" events
                List<Long> addLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataAdd(leads);
                for (Long leadId : addLeadIds) {
                    log.info("extract from add event for lead {}", leadId);
                    updateLead(leadId);
                }
                
                // Extract lead IDs from other event types (status, mail_in, etc.)
                List<Long> eventLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataEvents(leads);
                for (Long leadId : eventLeadIds) {
                    log.info("IDs from other event types {}", leadId);
                    updateLead(leadId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing form-data webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void processFormDataSyncOrderWebhook(String formData) {
        try {
            Map<String, Object> parsed = webhookParserService.parseFormDataWebhook(formData);
            Map<String, Object> leads = webhookParserService.extractLeadsFromFormData(parsed);

            if (leads != null) {
                List<Long> addLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataAdd(leads);
                for (Long leadId : addLeadIds) {
                    orderService.syncOrder(leadId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing form-data webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateLead(Long leadId) {
        var result = orderService.syncOrder(leadId);
        if (result.getSuccess()){
            leadAmoCrmStatusUpdater.moveToReadyToDeliver(leadId);
        } else {
            log.warn("Lead not updated because unsuccess sync");
        }
    }
}
