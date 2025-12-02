package ru.anyforms.integration;

import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoLead;
import ru.anyforms.model.AmoLeadStatus;
import ru.anyforms.model.AmoProduct;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для работы с amoCRM API
 */
public interface AmoCrmGateway {
    /**
     * Получает сделку по ID
     */
    AmoLead getLead(Long leadId);

    /**
     * Получает контакт по ID
     */
    AmoContact getContact(Long contactId);

    /**
     * Получает ID контакта из сделки
     */
    Long getContactIdFromLead(Long leadId);

    /**
     * Обновляет статус сделки
     */
    boolean updateLeadStatus(Long leadId, AmoLeadStatus status, Long pipelineId);

    /**
     * Обновляет статус сделки по ID статуса
     */
    boolean updateLeadStatus(Long leadId, Long statusId, Long pipelineId);

    /**
     * Обновляет кастомное поле сделки
     */
    boolean updateLeadCustomField(Long leadId, Long fieldId, String value);

    /**
     * Отправляет сообщение в последний мессенджер сделки
     */
    boolean sendMessageToContact(Long leadId, String message);

    /**
     * Обновляет несколько полей сделки одновременно
     */
    boolean updateLeadFields(Long leadId, Long price, Map<Long, String> customFields);

    /**
     * Добавляет примечание к сделке
     */
    boolean addNoteToLead(Long leadId, String noteText);

    /**
     * Получает товары из сделки
     */
    List<AmoProduct> getLeadProducts(Long leadId);
}

