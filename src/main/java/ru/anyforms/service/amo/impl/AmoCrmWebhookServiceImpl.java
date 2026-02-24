package ru.anyforms.service.amo.impl;

import lombok.extern.log4j.Log4j2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.service.amo.AmoCrmWebhookService;
import ru.anyforms.service.amo.LeadAmoCrmStatusUpdater;
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
    private final AmoCrmGateway amoCrmGateway;

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
                    handlePerchance(leadId);
                }
                
                // Extract lead IDs from other event types (status, mail_in, etc.)
                List<Long> eventLeadIds = jsonLeadIdExtraction.extractLeadIdsFromFormDataEvents(leads);
                for (Long leadId : eventLeadIds) {
                    log.info("IDs from other event types {}", leadId);
                    handlePerchance(leadId);
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

    private void handlePerchance(Long leadId) {
        var result = orderService.syncOrder(leadId);
        var contact = amoCrmGateway.getContactFromLead(leadId);
        var countReleasedOrders = parseLong(contact.getCustomFieldValue(AmoCrmFieldId.COUNT_RELEASED_ORDERS));
        countReleasedOrders++;
        amoCrmGateway.updateContactCustomField(leadId, AmoCrmFieldId.COUNT_RELEASED_ORDERS.getId(), String.valueOf(countReleasedOrders));
        if (result.getSuccess()){
            leadAmoCrmStatusUpdater.moveToReadyToDeliver(leadId);
        } else {
            log.warn("Lead not updated because unsuccess sync");
        }
    }

    private Long parseLong(String str) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            log.warn("не получилось спарсить стрингу", e);
        }
        return 0L;
    }
}
