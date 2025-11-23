package ru.anyforms.service;

import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoLead;
import ru.anyforms.service.DataExtractionService.ExtractedData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeadProcessingService {
    private final CacheService cacheService;
    private final AmoCrmService amoCrmService;
    private final GoogleSheetsService googleSheetsService;
    private final LeadValidationService leadValidationService;
    private final DataExtractionService dataExtractionService;
    private final DataConversionService dataConversionService;

    @Value("${amocrm.status.paid.id:}")
    private Long paidStatusId;

    @Value("${amocrm.status.ready.to.ship.id:}")
    private Long readyToShipStatusId;

    /**
     * Обрабатывает лид: проверяет кэш, валидирует, извлекает данные и добавляет в Google Sheets
     */
    public void processLead(Long leadId) {
        // Check if already processed
        if (cacheService.containsLead(leadId)) {
            return;
        }

        try {
            // Get lead data
            AmoLead lead = amoCrmService.getLead(leadId);
            if (lead == null) {
                return;
            }

            // Validate lead conditions
            if (!leadValidationService.isValidLead(lead)) {
                System.out.println("Lead is not valid: " + leadId);
                return; // Conditions not met
            }

            // Get contact ID from lead
            Long contactId = amoCrmService.getContactIdFromLead(leadId);
            if (contactId == null) {
                return;
            }

            // Get contact data
            AmoContact contact = amoCrmService.getContact(contactId);
            if (contact == null) {
                return;
            }

            // Extract data
            ExtractedData extractedData = dataExtractionService.extractData(lead, contact, leadId);

            // Convert to Google Sheets format
            List<Object> rowData = dataConversionService.convertToGoogleSheetsRow(extractedData);

            // Get sheet name from lead field 2482683 (multiselect field)
            String sheetNameFromLead = lead.getCustomFieldValue(2482683L);
            // If sheet name is not found in lead, use default from env
            if (sheetNameFromLead == null || sheetNameFromLead.trim().isEmpty()) {
                sheetNameFromLead = null; // Will use default from GoogleSheetsService
            }

            // Append to Google Sheets
            if (sheetNameFromLead != null) {
                googleSheetsService.appendRow(rowData, sheetNameFromLead);
            } else {
                googleSheetsService.appendRow(rowData); // Use default sheet name
            }

            // После успешного добавления в Google Sheets проверяем и меняем статус
            // Обернуто в try-catch, чтобы ошибка обновления статуса не прерывала процесс
            try {
                updateStatusIfNeeded(lead);
            } catch (Exception e) {
                System.err.println("Error updating status for lead " + leadId + ": " + e.getMessage());
                e.printStackTrace();
                // Продолжаем выполнение, так как данные уже добавлены в Google Sheets
            }

            // Add to cache
            cacheService.addLead(leadId);

        } catch (Exception e) {
            // Log error but don't throw to avoid breaking webhook processing
            System.err.println("Error processing lead " + leadId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Обновляет статус сделки с "Оплачен" на "готов к отправке" если текущий статус соответствует
     * @param lead сделка для проверки и обновления статуса
     */
    private void updateStatusIfNeeded(AmoLead lead) {
        // Проверяем, что ID статусов настроены
        if (paidStatusId == null || readyToShipStatusId == null) {
            System.out.println("Status IDs are not configured. Skipping status update.");
            return;
        }

        // Проверяем, что текущий статус соответствует "Оплачен"
        Long currentStatusId = lead.getStatusId();
        if (currentStatusId == null || !currentStatusId.equals(paidStatusId)) {
            System.out.println("Lead " + lead.getId() + " status is not 'Оплачен' (current: " + currentStatusId + "). Skipping status update.");
            return;
        }

        // Меняем статус на "готов к отправке"
        boolean updated = amoCrmService.updateLeadStatus(lead.getId(), readyToShipStatusId, lead.getPipelineId());
        if (updated) {
            System.out.println("Successfully changed status for lead " + lead.getId() + " from 'Оплачен' to 'готов к отправке'");
        } else {
            System.err.println("Failed to change status for lead " + lead.getId());
        }
    }
}

