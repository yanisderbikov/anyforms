package ru.anyforms.service.impl;

import ru.anyforms.dto.SyncOrderRequestDTO;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.integration.GoogleSheetsGateway;
import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoCrmFieldId;
import ru.anyforms.model.AmoLead;
import ru.anyforms.model.AmoLeadStatus;
import ru.anyforms.util.amo.DataConversionService;
import ru.anyforms.util.amo.DataExtractionService;
import ru.anyforms.service.LeadAmoCrmStatusUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class LeadAmoCrmStatusUpdaterImpl implements LeadAmoCrmStatusUpdater {
    private final CacheService cacheService;
    private final AmoCrmGateway amoCrmGateway;
    private final GoogleSheetsGateway googleSheetsGateway;
    private final DataExtractionService dataExtractionService;
    private final DataConversionService dataConversionService;
    private final OrderServiceImpl orderService;

    /**
     * Обрабатывает лид: проверяет кэш, валидирует, извлекает данные и добавляет в Google Sheets
     */
    @Deprecated
    @Override
    public void addLeadToOrderAndGoogleSheet(Long leadId) {
        // Check if already processed
        if (cacheService.containsLead(leadId)) {
            return;
        }

        try {
            // Get lead data
            AmoLead lead = amoCrmGateway.getLead(leadId);
            if (lead == null) {
                return;
            }

//            // Validate lead conditions
//            if (!leadValidationService.isValidLead(lead)) {
//                System.out.println("Lead is not valid: " + leadId);
//                return; // Conditions not met
//            }

            // Get contact ID from lead
            Long contactId = amoCrmGateway.getContactIdFromLead(leadId);
            if (contactId == null) {
                return;
            }

            // Get contact data
            AmoContact contact = amoCrmGateway.getContact(contactId);
            if (contact == null) {
                return;
            }

            // Extract data
            DataExtractionService.ExtractedData extractedData = dataExtractionService.extractData(lead, contact, leadId);

            // Convert to Google Sheets format
            List<Object> rowData = dataConversionService.convertToGoogleSheetsRow(extractedData);

            // Get sheet name from lead field (multiselect field)
            String sheetNameFromLead = lead.getCustomFieldValue(AmoCrmFieldId.PRODUCT_TYPE.getId());
            // If sheet name is not found in lead, use default from env
            if (sheetNameFromLead == null || sheetNameFromLead.trim().isEmpty()) {
                sheetNameFromLead = null; // Will use default from GoogleSheetsService
            }

            // Append to Google Sheets
            if (sheetNameFromLead != null) {
                googleSheetsGateway.appendRow(rowData, sheetNameFromLead);
            } else {
//                googleSheetsGateway.appendRow(rowData); // Use default sheet name
            }

            // После успешного добавления в Google Sheets проверяем и меняем статус
            // Обернуто в try-catch, чтобы ошибка обновления статуса не прерывала процесс
            try {
                moveToReadyToDeliver(lead);
            } catch (Exception e) {
                System.err.println("Error updating status for lead " + leadId + ": " + e.getMessage());
                e.printStackTrace();
                // Продолжаем выполнение, так как данные уже добавлены в Google Sheets
            }

            // Add to cache
            cacheService.addLead(leadId);
            SyncOrderRequestDTO syncRequest = new SyncOrderRequestDTO();
            syncRequest.setLeadId(leadId);
            orderService.syncOrder(syncRequest);

        } catch (Exception e) {
            // Log error but don't throw to avoid breaking webhook processing
            System.err.println("Error processing lead " + leadId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void moveToReadyToDeliver(Long leadId) {
        var lead = amoCrmGateway.getLead(leadId);
        moveToReadyToDeliver(lead);
    }

    /**
     * Обновляет статус сделки с "Оплачен" на "готов к отправке" если текущий статус соответствует
     * @param lead сделка для проверки и обновления статуса
     * @throws RuntimeException если в поле PRODUCT_TYPE выбрано 2 объекта
     */
    public void moveToReadyToDeliver(AmoLead lead) {

        Long currentStatusId = lead.getStatusId();
        AmoLeadStatus currentStatus = AmoLeadStatus.fromStatusId(currentStatusId);
        if (currentStatus != AmoLeadStatus.PAID) {
            System.out.println("Lead " + lead.getId() + " status is not '" + AmoLeadStatus.PAID.getDescription() +
                    "' (current: " + currentStatus.getDescription() + " / " + currentStatusId + "). Skipping status update.");
            return;
        }

        boolean updated = amoCrmGateway.updateLeadStatus(lead.getId(), AmoLeadStatus.READY_TO_SHIP, lead.getPipelineId());
        if (updated) {
            System.out.println("Successfully changed status for lead " + lead.getId() + 
                    " from '" + AmoLeadStatus.PAID.getDescription() + "' to '" + AmoLeadStatus.READY_TO_SHIP.getDescription() + "'");
        } else {
            System.err.println("Failed to change status for lead " + lead.getId());
        }
    }
}

