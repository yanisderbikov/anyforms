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
     * @param minutesToComplete срок в минутах: 0 — выполнить сейчас, N — в течение N часов
     */
    void setNewTask(Long responsibleUser, Long taskType, String taskMessage, Long leadId, int minutesToComplete);

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
     * Получает полный контакт по ID сделки (сначала извлекает contactId из сделки, затем загружает контакт).
     *
     * @param leadId ID сделки
     * @return контакт или null, если у сделки нет контакта
     */
    AmoContact getContactFromLead(Long leadId);

    boolean updateLeadStatus(Long leadId, AmoLeadStatus status);

    /**
     * Обновляет статус сделки
     */
    boolean updateLeadStatus(Long leadId, AmoLeadStatus status, Long pipelineId);

    /**
     * Обновляет статус сделки по ID статуса
     */
    boolean updateLeadStatus(Long leadId, Long statusId, Long pipelineId);

    boolean updateLeadStatus(List<Long> leadIds, Long statusId, Long pipelineId);

    /**
     * Обновляет кастомное поле сделки
     */
    boolean updateLeadCustomField(Long leadId, Long fieldId, String value);

    /**
     * Обновляет кастомное поле контакта
     *
     * @param contactId ID контакта
     * @param fieldId   ID кастомного поля
     * @param value     новое значение
     * @return true при успехе
     */
    default boolean updateContactCustomField(Long contactId, Long fieldId, String value) {
        return updateContactCustomField(contactId, Map.of(fieldId, value));
    }

    /**
     * Обновляет несколько кастомных полей контакта одним запросом.
     *
     * @param contactId    ID контакта
     * @param customFields мапа fieldId -> значение
     * @return true при успехе
     */
    boolean updateContactCustomField(Long contactId, Map<Long, String> customFields);

    /**
     * Отправляет сообщение в последний мессенджер сделки
     */
    boolean sendMessageToContact(Long leadId, String message);

    /**
     * Обновляет несколько полей сделки одновременно.
     * Числовые значения (например, timestamp для полей-дат) сериализуются числом — АМО
     * не принимает строку для date-полей.
     */
    boolean updateLeadFields(Long leadId, Long price, Map<Long, ?> customFields);

    default boolean updateLeadFields(Long leadId, Map<Long, ?> customFields) {
        return updateLeadFields(leadId, null, customFields);
    }

    /**
     * Добавляет примечание к сделке
     */
    boolean addNoteToLead(Long leadId, String noteText);

    /**
     * Получает товары из сделки
     */
    List<AmoProduct> getLeadProducts(Long leadId);


    List<Long> getLeadIdsOlderThanTwoWeeks(Long pipelineId, Long statusId, Long closedTo);

    /**
     * Запрос №1 дрип-кампании: получить ID всех лидов в заданной воронке/статусе.
     * <p>
     * TODO(пагинация): сейчас один запрос без постраничной выборки (лидов десятки).
     * При росте объёма добавить обход страниц amoCRM ({@code page}/{@code limit}).
     *
     * @param pipelineId ID воронки
     * @param statusId   ID статуса
     * @return список lead_id (может быть пустым)
     */
    List<Long> getLeadIdsByStatus(Long pipelineId, Long statusId);

    /**
     * Запрос №2 дрип-кампании: запустить SalesBot для сделки (fire-and-forget).
     * <p>
     * TODO: уточнить точный формат запроса запуска SalesBot в amoCRM (endpoint и тело).
     * Предполагается {@code POST /api/v4/salesbot/run} с телом
     * {@code [{"bot_id":<botId>, "entity_id":<leadId>, "entity_type":2}]} (2 = leads).
     *
     * @return {@code true}, если запрос ушёл успешно.
     */
    boolean runSalesbot(Long leadId, Long botId);

    /**
     * Создаёт новую заявку из лендинга с вложенным контактом.
     *
     * @param leadName название сделки
     * @param contactName имя контакта
     * @param phone номер телефона контакта
     * @param pipelineId ID воронки; {@code null} — воронка лендинга по умолчанию
     * @param statusId ID статуса; {@code null} — статус лендинга по умолчанию
     * @param utmByFieldCode UTM-метки по кодам системных полей amo (UTM_SOURCE, UTM_MEDIUM…); может быть пустым
     * @return ID созданной сделки
     */
    Long createLandingLead(String leadName, String contactName, String phone,
                           Long pipelineId, Long statusId, Map<String, String> utmByFieldCode);

    /**
     * Создаёт сделку с вложенным контактом в заданной воронке и статусе
     * (маркетплейс: розничная воронка, статус «Готов к отправке»).
     *
     * @return ID созданной сделки
     */
    Long createLead(String leadName, String contactName, String phone, Long pipelineId, Long statusId);

    /**
     * Привязывает товары каталога к сделке (POST /api/v4/leads/{id}/link).
     *
     * @param leadId              ID сделки
     * @param catalogId           ID каталога товаров
     * @param elementIdToQuantity ID элемента каталога → количество
     * @return true при успехе
     */
    boolean linkCatalogElementsToLead(Long leadId, Long catalogId, Map<Long, Integer> elementIdToQuantity);

    /**
     * Возвращает все элементы (товары) каталога АМО: id + name (+ catalogId).
     * Для выпадающего списка товаров в админке маркетплейса.
     *
     * @param catalogId ID каталога товаров
     */
    List<AmoProduct> getCatalogElements(Long catalogId);
}
