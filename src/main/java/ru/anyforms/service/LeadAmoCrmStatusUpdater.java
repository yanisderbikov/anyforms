package ru.anyforms.service;

import ru.anyforms.model.AmoLead;

/**
 * Интерфейс для обработки лидов из AmoCRM
 */
public interface LeadAmoCrmStatusUpdater {
    /**
     * Обрабатывает лид: проверяет кэш, валидирует, извлекает данные и добавляет в Google Sheets
     */
    void addLeadToOrderAndGoogleSheet(Long leadId);
    void updateStatusIfNeeded(AmoLead lead);
    void updateStatusIfNeeded(Long leadId);
}