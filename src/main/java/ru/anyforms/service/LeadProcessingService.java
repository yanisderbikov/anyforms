package ru.anyforms.service;

/**
 * Интерфейс для обработки лидов из AmoCRM
 */
public interface LeadProcessingService {
    /**
     * Обрабатывает лид: проверяет кэш, валидирует, извлекает данные и добавляет в Google Sheets
     */
    void addLeadToOrderAndGoogleSheet(Long leadId);
}
