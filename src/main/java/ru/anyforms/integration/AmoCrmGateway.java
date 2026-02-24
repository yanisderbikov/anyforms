package ru.anyforms.integration;

import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.amo.AmoProduct;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для работы с amoCRM API
 */
public interface AmoCrmGateway {

    /**
     * Создаёт задачу в amoCRM.
     *
     * @param responsibleUser ID ответственного (можно null — будет текущий пользователь)
     * @param taskType        тип задачи: 1 — Звонок, 2 — Встреча (можно null)
     * @param taskMessage     текст задачи
     * @param leadId          ID сделки, к которой привязать задачу (можно null)
     * @param hoursToComplete срок в часах: 0 — выполнить сейчас, N — в течение N часов
     */
    void setNewTask(Long responsibleUser, Long taskType, String taskMessage, Long leadId, int hoursToComplete);

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

    boolean updateLeadStatus(Long leadId, AmoLeadStatus status);

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
