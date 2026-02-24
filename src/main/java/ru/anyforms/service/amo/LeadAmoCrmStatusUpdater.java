package ru.anyforms.service.amo;

import ru.anyforms.model.amo.AmoLead;

/**
 * Интерфейс для обработки лидов из AmoCRM
 */
public interface LeadAmoCrmStatusUpdater {
    /**
     * Обрабатывает лид: проверяет кэш, валидирует, извлекает данные и добавляет в Google Sheets
     */
    void addLeadToOrderAndGoogleSheet(Long leadId);
    void moveToReadyToDeliver(AmoLead lead);
    void moveToReadyToDeliver(Long leadId);
}