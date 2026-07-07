package ru.anyforms.integration.impl;

import lombok.extern.slf4j.Slf4j;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.SalesbotRunRequest;
import ru.anyforms.model.amo.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
class AmoCrmHttpGateway implements AmoCrmGateway {
    private WebClient webClient;
    private final Gson gson;
    
    @Value("${amocrm.subdomain}")
    private String subdomain;
    
    @Value("${amocrm.access.token}")
    private String accessToken;

    @Value("${amocrm.landing.responsible.user.id}")
    private Long landingResponsibleUserId;

    @Value("${amocrm.landing.pipeline.id}")
    private Long landingPipelineId;

    @Value("${amocrm.products.catalog.id}")
    private Long productsCatalogId;

    @Value("${amocrm.landing.status.id}")
    private Long landingStatusId;

    public AmoCrmHttpGateway() {
        this.gson = new Gson();
    }

    /** Лимит буфера ответа WebClient: ответы amo (списки лидов) превышают дефолтные 256 КБ. */
    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024; // 16 МБ

    @jakarta.annotation.PostConstruct
    private void init() {
        this.webClient = WebClient.builder()
                .baseUrl("https://" + subdomain + ".amocrm.ru")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    @Override
    public void setNewTask(Long responsibleUser, Long taskType, String taskMessage, Long leadId, int minutesToComplete) {
        try {
            // complete_till: 0 часов = сейчас, иначе текущее время + hoursToComplete часов
            long nowSec = System.currentTimeMillis() / 1000;
            long completeTill = minutesToComplete <= 0 ? nowSec : nowSec + (long) minutesToComplete * 60;

            JsonObject task = new JsonObject();
            task.addProperty("text", taskMessage != null ? taskMessage : "");
            task.addProperty("complete_till", completeTill);
            task.addProperty("task_type_id", taskType);
            if (responsibleUser != null) {
                task.addProperty("responsible_user_id", responsibleUser);
            }
            if (leadId != null) {
                task.addProperty("entity_id", leadId);
                task.addProperty("entity_type", "leads");
            }

            JsonArray tasksArray = new JsonArray();
            tasksArray.add(task);

            String url = "/api/v4/tasks";
            webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(tasksArray.toString())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException("AmoCRM tasks API " + response.statusCode() + ": " + body))))
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {
            log.error("setNewTask failed: {}", e.getMessage());
            throw new RuntimeException("Failed to create task in amoCRM", e);
        }
    }

    @Override
    public AmoLead getLead(Long leadId) {
        try {
            String url = "/api/v4/leads/" + leadId;
            String response = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // amoCRM API returns single entity directly or in _embedded.leads array
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            if (jsonObject.has("_embedded")) {
                JsonObject embedded = jsonObject.getAsJsonObject("_embedded");
                if (embedded.has("leads")) {
                    var leads = embedded.getAsJsonArray("leads");
                    if (leads != null && leads.size() > 0) {
                        return gson.fromJson(leads.get(0), AmoLead.class);
                    }
                }
            }
            // Try to parse as direct object
            return gson.fromJson(jsonObject, AmoLead.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get lead from amoCRM", e);
        }
    }

    @Override
    public AmoContact getContact(Long contactId) {
        try {
            String url = "/api/v4/contacts/" + contactId;
            String response = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // amoCRM API returns single entity directly or in _embedded.contacts array
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            AmoContact contact = null;
            if (jsonObject.has("_embedded")) {
                JsonObject embedded = jsonObject.getAsJsonObject("_embedded");
                if (embedded.has("contacts")) {
                    var contacts = embedded.getAsJsonArray("contacts");
                    if (contacts != null && contacts.size() > 0) {
                        contact = gson.fromJson(contacts.get(0), AmoContact.class);
                    }
                }
            }
            // Try to parse as direct object
            if (contact == null) {
                contact = gson.fromJson(jsonObject, AmoContact.class);
            }
            
            // Устанавливаем телефон из кастомного поля
            if (contact != null) {
                String phoneValue = contact.getCustomFieldValue(AmoCrmFieldId.PHONE_CONTACT.getId());
                if (phoneValue != null) {
                    AmoContact.Phone phone = new AmoContact.Phone();
                    phone.setValue(phoneValue);
                    contact.setPhone(java.util.Collections.singletonList(phone));
                }
            }
            
            return contact;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get contact from amoCRM", e);
        }
    }

    @Override
    public Long getContactIdFromLead(Long leadId) {
        try {
            String url = "/api/v4/leads/" + leadId + "?with=contacts";
            String response = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse JSON to get contact ID from _embedded.contacts
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            if (jsonObject.has("_embedded")) {
                JsonObject embedded = jsonObject.getAsJsonObject("_embedded");
                if (embedded.has("contacts")) {
                    var contacts = embedded.getAsJsonArray("contacts");
                    if (contacts != null && contacts.size() > 0) {
                        JsonObject firstContact = contacts.get(0).getAsJsonObject();
                        if (firstContact.has("id") && !firstContact.get("id").isJsonNull()) {
                            return firstContact.get("id").getAsLong();
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get contact ID from lead", e);
        }
    }

    @Override
    public AmoContact getContactFromLead(Long leadId) {
        Long contactId = getContactIdFromLead(leadId);
        if (contactId == null) {
            return null;
        }
        return getContact(contactId);
    }

    @Override
    public boolean updateLeadStatus(Long leadId, AmoLeadStatus status) {
        if (status == null || status.getStatusId() == null) {
            log.error("Status ID is not configured for status: {}", status != null ? status.getDescription() : "null");
            return false;
        }
        return updateLeadStatus(leadId, status.getStatusId(), null);
    }

    @Override
    public boolean updateLeadStatus(Long leadId, AmoLeadStatus status, Long pipelineId) {
        if (status == null || status.getStatusId() == null) {
            log.error("Status ID is not configured for status: {}", status != null ? status.getDescription() : "null");
            return false;
        }
        return updateLeadStatus(leadId, status.getStatusId(), pipelineId);
    }

    @Override
    public boolean updateLeadStatus(Long leadId, Long statusId, Long pipelineId) {
        try {
            // Если pipelineId не указан, получаем текущую воронку сделки
            if (pipelineId == null) {
                AmoLead lead = getLead(leadId);
                if (lead == null || lead.getPipelineId() == null) {
                    log.error("Failed to get pipeline ID for lead {}", leadId);
                    return false;
                }
                pipelineId = lead.getPipelineId();
            }

            // Формируем JSON для обновления статуса
            JsonObject leadUpdate = new JsonObject();
            leadUpdate.addProperty("id", leadId);
            leadUpdate.addProperty("status_id", statusId);
            if (pipelineId != null) {
                leadUpdate.addProperty("pipeline_id", pipelineId);
            }

            JsonArray leadsArray = new JsonArray();
            leadsArray.add(leadUpdate);

            String url = "/api/v4/leads";
            String response = webClient.patch()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(leadsArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Пытаемся определить статус для логирования
            AmoLeadStatus status = AmoLeadStatus.fromStatusId(statusId);
            String statusDescription = status != AmoLeadStatus.UNKNOWN 
                    ? status.getDescription() + " (" + statusId + ")"
                    : String.valueOf(statusId);
            log.info("Successfully updated status for lead {} to {}", leadId, statusDescription);
            return true;
        } catch (Exception e) {
            log.error("Failed to update lead status in amoCRM for lead {}", leadId, e);
            return false;
        }
    }

    @Override
    public boolean updateLeadStatus(List<Long> leadIds, Long statusId, Long pipelineId) {
        if (leadIds == null || leadIds.isEmpty()) {
            log.error("Lead IDs list is empty");
            return false;
        }

        if (statusId == null) {
            log.error("Status ID is null");
            return false;
        }

        try {
            JsonArray leadsArray = new JsonArray();

            for (Long leadId : leadIds) {
                if (leadId == null) {
                    continue;
                }

                JsonObject leadUpdate = new JsonObject();
                leadUpdate.addProperty("id", leadId);
                leadUpdate.addProperty("status_id", statusId);

                if (pipelineId != null) {
                    leadUpdate.addProperty("pipeline_id", pipelineId);
                }

                leadsArray.add(leadUpdate);
            }

            if (leadsArray.size() == 0) {
                log.error("No valid lead IDs to update");
                return false;
            }

            String url = "/api/v4/leads";
            webClient.patch()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(leadsArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            AmoLeadStatus status = AmoLeadStatus.fromStatusId(statusId);
            String statusDescription = status != AmoLeadStatus.UNKNOWN
                    ? status.getDescription() + " (" + statusId + ")"
                    : String.valueOf(statusId);

            log.info("Successfully updated {} leads to status {}", leadsArray, statusDescription);
            return true;

        } catch (Exception e) {
            log.error("Failed to bulk update lead statuses", e);
            return false;
        }
    }

    @Override
    public boolean updateLeadCustomField(Long leadId, Long fieldId, String value) {
        try {
            // Получаем текущую сделку для сохранения других полей
            AmoLead lead = getLead(leadId);
            if (lead == null) {
                log.error("Failed to get lead {}", leadId);
                return false;
            }

            // Формируем JSON для обновления кастомного поля
            JsonObject leadUpdate = new JsonObject();
            leadUpdate.addProperty("id", leadId);

            // Создаем массив кастомных полей
            JsonArray customFieldsArray = new JsonArray();
            JsonObject customField = new JsonObject();
            customField.addProperty("field_id", fieldId);
            
            JsonArray valuesArray = new JsonArray();
            JsonObject fieldValue = new JsonObject();
            fieldValue.addProperty("value", value);
            valuesArray.add(fieldValue);
            
            customField.add("values", valuesArray);
            customFieldsArray.add(customField);
            
            leadUpdate.add("custom_fields_values", customFieldsArray);

            JsonArray leadsArray = new JsonArray();
            leadsArray.add(leadUpdate);

            String url = "/api/v4/leads";
            String response = webClient.patch()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(leadsArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully updated custom field {} for lead {} with value: {}", fieldId, leadId, value);
            return true;
        } catch (Exception e) {
            log.error("Failed to update custom field in amoCRM for lead {}, field {}", leadId, fieldId, e);
            return false;
        }
    }



    @Override
    public boolean updateContactCustomField(Long contactId, Map<Long, String> customFields) {
        if (customFields == null || customFields.isEmpty()) {
            return true;
        }
        try {
            JsonArray customFieldsArray = new JsonArray();
            for (Map.Entry<Long, String> entry : customFields.entrySet()) {
                JsonObject customField = new JsonObject();
                customField.addProperty("field_id", entry.getKey());
                JsonArray valuesArray = new JsonArray();
                JsonObject fieldValue = new JsonObject();
                addValueToJson(fieldValue, entry.getValue());
                valuesArray.add(fieldValue);
                customField.add("values", valuesArray);
                customFieldsArray.add(customField);
            }

            JsonObject body = new JsonObject();
            body.add("custom_fields_values", customFieldsArray);

            String url = "/api/v4/contacts/" + contactId;
            String bodyStr = body.toString();
            log.debug("PATCH contact custom fields: {} body: {}", url, bodyStr);

            webClient.patch()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(bodyStr)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully updated {} custom field(s) for contact {}", customFields.size(), contactId);
            return true;
        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("amoCRM PATCH contact (multiple fields) failed: contactId={}, status={}, response={}", contactId, e.getStatusCode(), responseBody, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to update contact custom fields in amoCRM for contact {}", contactId, e);
            return false;
        }
    }

    /**
     * Добавляет в JSON объект поле "value" как число или строку в зависимости от содержимого.
     * amoCRM для числовых полей ожидает number (например 10), не строку "10".
     */
    private void addValueToJson(JsonObject fieldValue, String value) {
        if (value == null) {
            fieldValue.addProperty("value", (String) null);
            return;
        }
        try {
            long l = Long.parseLong(value.trim());
            fieldValue.addProperty("value", l);
        } catch (NumberFormatException e1) {
            try {
                double d = Double.parseDouble(value.trim().replace(',', '.'));
                fieldValue.addProperty("value", d);
            } catch (NumberFormatException e2) {
                fieldValue.addProperty("value", value);
            }
        }
    }

    @Override
    public boolean sendMessageToContact(Long leadId, String message) {
        try {
            // Получаем список чатов сделки
            String chatsUrl = "/api/v4/chats?entity_id=" + leadId + "&entity_type=lead";
            String chatsResponse;
            try {
                chatsResponse = webClient.get()
                        .uri(chatsUrl)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (WebClientResponseException.NotFound e) {
                // Если чаты не найдены (404), пробуем альтернативный вариант или fallback
                log.warn("Chats endpoint returned 404 for lead {}, trying alternative method", leadId);
                // Пробуем получить чаты через контакт как fallback
                Long contactId = getContactIdFromLead(leadId);
                if (contactId != null) {
                    return sendMessageViaUnsorted(contactId, message);
                }
                return false;
            }

            if (chatsResponse == null || chatsResponse.trim().isEmpty()) {
                log.warn("Failed to get chats for lead {}, trying fallback method", leadId);
                // Пробуем получить чаты через контакт как fallback
                Long contactId = getContactIdFromLead(leadId);
                if (contactId != null) {
                    return sendMessageViaUnsorted(contactId, message);
                }
                return false;
            }

            // Парсим ответ и находим последний активный чат
            JsonObject chatsJson = JsonParser.parseString(chatsResponse).getAsJsonObject();
            JsonArray chatsArray = null;
            
            if (chatsJson.has("_embedded") && chatsJson.getAsJsonObject("_embedded").has("chats")) {
                chatsArray = chatsJson.getAsJsonObject("_embedded").getAsJsonArray("chats");
            } else if (chatsJson.has("chats")) {
                chatsArray = chatsJson.getAsJsonArray("chats");
            }

            if (chatsArray == null || chatsArray.size() == 0) {
                log.warn("No chats found for lead {}", leadId);
                // Пробуем отправить через unsorted API как fallback
                Long contactId = getContactIdFromLead(leadId);
                if (contactId != null) {
                    return sendMessageViaUnsorted(contactId, message);
                }
                return false;
            }

            // Берем последний чат (обычно это последний активный)
            JsonObject lastChat = chatsArray.get(chatsArray.size() - 1).getAsJsonObject();
            Long chatId = null;
            if (lastChat.has("id") && !lastChat.get("id").isJsonNull()) {
                chatId = lastChat.get("id").getAsLong();
            }

            if (chatId == null) {
                log.warn("Failed to get chat ID for lead {}", leadId);
                // Пробуем отправить через unsorted API как fallback
                Long contactId = getContactIdFromLead(leadId);
                if (contactId != null) {
                    return sendMessageViaUnsorted(contactId, message);
                }
                return false;
            }

            // Отправляем сообщение в чат
            JsonObject messageObj = new JsonObject();
            messageObj.addProperty("text", message);

            JsonArray messagesArray = new JsonArray();
            messagesArray.add(messageObj);

            String messageUrl = "/api/v4/chats/" + chatId + "/messages";
            String response = webClient.post()
                    .uri(messageUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(messagesArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully sent message to chat {} for lead {}", chatId, leadId);
            return true;
        } catch (Exception e) {
            log.error("Failed to send message in amoCRM for lead {}", leadId, e);
            // Пробуем fallback метод
            try {
                Long contactId = getContactIdFromLead(leadId);
                if (contactId != null) {
                    return sendMessageViaUnsorted(contactId, message);
                }
            } catch (Exception ex) {
                log.debug("Fallback send message failed for lead {}", leadId, ex);
            }
            return false;
        }
    }

    @Override
    public boolean updateLeadFields(Long leadId, Long price, Map<Long, String> customFields) {
        try {
            // Формируем JSON для обновления полей
            JsonObject leadUpdate = new JsonObject();
            leadUpdate.addProperty("id", leadId);
            
            // Добавляем price если указан
            if (price != null) {
                leadUpdate.addProperty("price", price);
            }
            
            // Добавляем кастомные поля если есть
            if (customFields != null && !customFields.isEmpty()) {
                JsonArray customFieldsArray = new JsonArray();
                for (Map.Entry<Long, String> entry : customFields.entrySet()) {
                    JsonObject customField = new JsonObject();
                    customField.addProperty("field_id", entry.getKey());
                    
                    JsonArray valuesArray = new JsonArray();
                    JsonObject fieldValue = new JsonObject();
                    fieldValue.addProperty("value", entry.getValue());
                    valuesArray.add(fieldValue);
                    
                    customField.add("values", valuesArray);
                    customFieldsArray.add(customField);
                }
                leadUpdate.add("custom_fields_values", customFieldsArray);
            }

            JsonArray leadsArray = new JsonArray();
            leadsArray.add(leadUpdate);

            String url = "/api/v4/leads";
            String response = webClient.patch()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(leadsArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully updated fields for lead {}", leadId);
            return true;
        } catch (Exception e) {
            log.error("Failed to update lead fields in amoCRM for lead {}", leadId, e);
            return false;
        }
    }

    /**
     * Отправляет сообщение через unsorted API (fallback метод)
     */
    private boolean sendMessageViaUnsorted(Long contactId, String message) {
        try {
            AmoContact contact = getContact(contactId);
            if (contact == null) {
                return false;
            }

            String phone = null;
            if (contact.getPhone() != null && !contact.getPhone().isEmpty()) {
                phone = contact.getPhone().get(contact.getPhone().size() - 1).getValue();
            }

            if (phone == null || phone.trim().isEmpty()) {
                return false;
            }

            JsonObject unsortedRequest = new JsonObject();
            unsortedRequest.addProperty("source_name", "api");
            unsortedRequest.addProperty("source_uid", "api_" + System.currentTimeMillis());
            unsortedRequest.addProperty("created_at", System.currentTimeMillis() / 1000);

            JsonObject contactObj = new JsonObject();
            JsonArray phonesArray = new JsonArray();
            JsonObject phoneObj = new JsonObject();
            phoneObj.addProperty("value", phone);
            phonesArray.add(phoneObj);
            contactObj.add("phone", phonesArray);

            JsonObject messageObj = new JsonObject();
            messageObj.addProperty("text", message);
            messageObj.addProperty("service", "whatsapp");

            JsonArray messagesArray = new JsonArray();
            messagesArray.add(messageObj);

            unsortedRequest.add("contact", contactObj);
            unsortedRequest.add("messages", messagesArray);

            JsonArray unsortedArray = new JsonArray();
            unsortedArray.add(unsortedRequest);

            String url = "/api/v4/leads/unsorted";
            webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(unsortedArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully sent message via unsorted API for contact {}", contactId);
            return true;
        } catch (Exception e) {
            log.error("Failed to send message via unsorted API for contact {}", contactId, e);
            return false;
        }
    }

    @Override
    public boolean addNoteToLead(Long leadId, String noteText) {
        try {
            JsonObject note = new JsonObject();
            note.addProperty("entity_id", leadId);
            note.addProperty("note_type", "common");
            note.addProperty("text", noteText);

            JsonArray notesArray = new JsonArray();
            notesArray.add(note);

            String url = "/api/v4/leads/" + leadId + "/notes";
            String response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(notesArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully added note to lead {}", leadId);
            return true;
        } catch (Exception e) {
            log.error("Failed to add note to lead in amoCRM for lead {}", leadId, e);
            return false;
        }
    }

    @Override
    public List<AmoProduct> getLeadProducts(Long leadId) {
        try {
            // Получаем сделку с товарами через параметр with=catalog_elements
            String leadUrl = "/api/v4/leads/" + leadId + "?with=catalog_elements";
            String leadResponse = webClient.get()
                    .uri(leadUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (leadResponse == null || leadResponse.trim().isEmpty()) {
                return new java.util.ArrayList<>();
            }

            JsonObject leadJson = JsonParser.parseString(leadResponse).getAsJsonObject();
            List<AmoProduct> products = new java.util.ArrayList<>();
            java.util.Map<Long, JsonObject> productMetadataMap = new java.util.HashMap<>();

            // Извлекаем товары из _embedded.catalog_elements
            if (leadJson.has("_embedded")) {
                JsonObject embedded = leadJson.getAsJsonObject("_embedded");
                if (embedded.has("catalog_elements")) {
                    JsonArray catalogElements = embedded.getAsJsonArray("catalog_elements");
                    if (catalogElements != null) {
                        for (int i = 0; i < catalogElements.size(); i++) {
                            JsonObject catalogElement = catalogElements.get(i).getAsJsonObject();
                            Long elementId = catalogElement.has("id") ? catalogElement.get("id").getAsLong() : null;
                            
                            if (elementId != null) {
                                // Сохраняем metadata для этого товара
                                productMetadataMap.put(elementId, catalogElement);
                            }
                        }
                    }
                }
            }

            if (productMetadataMap.isEmpty()) {
                return products;
            }

            // Получаем детали товаров (название и т.д.) из каталога
            Long catalogId = productsCatalogId;
            List<Long> productIds = new java.util.ArrayList<>(productMetadataMap.keySet());
            
            // AmoCRM API позволяет получить до 250 элементов за раз
            // Разбиваем на батчи по 250 элементов
            int batchSize = 250;
            for (int i = 0; i < productIds.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, productIds.size());
                List<Long> batch = productIds.subList(i, endIndex);
                
                // Формируем URL с фильтром по ID (формат: filter[id][]=1&filter[id][]=2)
                StringBuilder urlBuilder = new StringBuilder("/api/v4/catalogs/");
                urlBuilder.append(catalogId);
                urlBuilder.append("/elements?");
                for (int j = 0; j < batch.size(); j++) {
                    if (j > 0) urlBuilder.append("&");
                    urlBuilder.append("filter[id][]=").append(batch.get(j));
                }
                
                String catalogUrl = urlBuilder.toString();
                String catalogResponse = webClient.get()
                        .uri(catalogUrl)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (catalogResponse != null && !catalogResponse.trim().isEmpty()) {
                    JsonObject catalogJson = JsonParser.parseString(catalogResponse).getAsJsonObject();
                    
                    // Парсим товары из каталога
                    if (catalogJson.has("_embedded")) {
                        JsonObject catalogEmbedded = catalogJson.getAsJsonObject("_embedded");
                        if (catalogEmbedded.has("elements")) {
                            JsonArray elements = catalogEmbedded.getAsJsonArray("elements");
                            if (elements != null) {
                                for (int j = 0; j < elements.size(); j++) {
                                    JsonObject element = elements.get(j).getAsJsonObject();
                                    Long elementId = element.has("id") ? element.get("id").getAsLong() : null;
                                    
                                    if (elementId != null && productMetadataMap.containsKey(elementId)) {
                                        AmoProduct product = new AmoProduct();
                                        product.setId(elementId);
                                        product.setName(element.has("name") ? element.get("name").getAsString() : null);
                                        
                                        // Получаем metadata из catalog_elements
                                        JsonObject catalogElement = productMetadataMap.get(elementId);
                                        if (catalogElement.has("metadata")) {
                                            JsonObject metadata = catalogElement.getAsJsonObject("metadata");
                                            if (metadata.has("quantity")) {
                                                product.setQuantity(metadata.get("quantity").getAsInt());
                                            }
                                            if (metadata.has("catalog_id")) {
                                                product.setCatalogId(metadata.get("catalog_id").getAsLong());
                                            }
                                            // Цена может быть в metadata.price или нужно получать из price_id
                                            // Пока оставляем null, если нужно - можно получить из каталога
                                        }
                                        
                                        products.add(product);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return products;
        } catch (Exception e) {
            log.error("Failed to get products from lead {}", leadId, e);
            return new java.util.ArrayList<>();
        }
    }

    @Override
    public List<AmoProduct> getCatalogElements(Long catalogId) {
        List<AmoProduct> products = new java.util.ArrayList<>();
        if (catalogId == null || catalogId <= 0) {
            log.warn("getCatalogElements: не задан catalogId");
            return products;
        }
        try {
            int limit = 250;
            for (int page = 1; page <= 100; page++) { // страховочный потолок 25000 товаров
                String url = "/api/v4/catalogs/" + catalogId + "/elements?limit=" + limit + "&page=" + page;
                String response = webClient.get()
                        .uri(url)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (response == null || response.trim().isEmpty()) {
                    break;
                }
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                if (!json.has("_embedded")) {
                    break;
                }
                JsonObject embedded = json.getAsJsonObject("_embedded");
                if (!embedded.has("elements")) {
                    break;
                }
                JsonArray elements = embedded.getAsJsonArray("elements");
                if (elements == null || elements.isEmpty()) {
                    break;
                }
                for (int i = 0; i < elements.size(); i++) {
                    JsonObject element = elements.get(i).getAsJsonObject();
                    if (!element.has("id") || element.get("id").isJsonNull()) {
                        continue;
                    }
                    AmoProduct product = new AmoProduct();
                    product.setId(element.get("id").getAsLong());
                    product.setName(element.has("name") && !element.get("name").isJsonNull()
                            ? element.get("name").getAsString() : null);
                    product.setCatalogId(catalogId);
                    products.add(product);
                }
                if (elements.size() < limit) {
                    break; // последняя страница
                }
            }
            return products;
        } catch (Exception e) {
            log.error("Failed to get catalog {} elements", catalogId, e);
            return products;
        }
    }

    @Override
    public List<Long> getLeadIdsOlderThanTwoWeeks(Long pipelineId,
                                                  Long statusId,
                                                  Long closedTo) {
        try {

            String url = "/api/v4/leads"
                    + "?filter[pipeline_id]=" + pipelineId
                    + "&filter[status_id]=" + statusId
                    + "&filter[closed_at][to]=" + closedTo
                    + "&limit=" + 50;

            String response = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<Long> result = new java.util.ArrayList<>();

            if (response == null || response.isEmpty()) {
                return result;
            }

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.has("_embedded")) {
                JsonObject embedded = json.getAsJsonObject("_embedded");
                if (embedded.has("leads")) {
                    JsonArray leads = embedded.getAsJsonArray("leads");

                    for (int i = 0; i < leads.size(); i++) {
                        JsonObject lead = leads.get(i).getAsJsonObject();
                        if (lead.has("id") && !lead.get("id").isJsonNull()) {
                            result.add(lead.get("id").getAsLong());
                        }
                    }
                }
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to get lead IDs", e);
        }
    }

    /** amoCRM не отдаёт больше 250 сущностей на страницу — обходим страницы. */
    private static final int AMO_PAGE_LIMIT = 250;
    /** Предохранитель от бесконечного цикла: 200 страниц = до 50 000 лидов. */
    private static final int MAX_PAGES = 200;

    @Override
    public List<Long> getLeadIdsByStatus(Long pipelineId, Long statusId) {
        // amoCRM фильтрует по статусу ТОЛЬКО через массив filter[statuses][N][...].
        // Плоский filter[status_id] не работает (возвращает пусто).
        // Пагинация: limit=250 (максимум amo), идём по page=1..N, пока страницы заполнены.
        List<Long> result = new java.util.ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            try {
                String url = "/api/v4/leads"
                        + "?filter[statuses][0][pipeline_id]=" + pipelineId
                        + "&filter[statuses][0][status_id]=" + statusId
                        + "&page=" + page
                        + "&limit=" + AMO_PAGE_LIMIT;

                String response = webClient.get()
                        .uri(url)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                // amo отдаёт 204/пустое тело, когда страниц больше нет.
                if (response == null || response.isEmpty()) {
                    break;
                }

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                if (!json.has("_embedded")) {
                    break;
                }
                JsonArray leads = json.getAsJsonObject("_embedded").getAsJsonArray("leads");
                if (leads == null || leads.isEmpty()) {
                    break;
                }

                for (int i = 0; i < leads.size(); i++) {
                    JsonObject lead = leads.get(i).getAsJsonObject();
                    if (lead.has("id") && !lead.get("id").isJsonNull()) {
                        result.add(lead.get("id").getAsLong());
                    }
                }

                // Неполная страница — значит последняя.
                if (leads.size() < AMO_PAGE_LIMIT) {
                    break;
                }
            } catch (Exception e) {
                log.error("Failed to get leads by status: pipeline={}, status={}, page={}",
                        pipelineId, statusId, page, e);
                break;
            }
        }
        log.info("getLeadIdsByStatus: pipeline={}, status={} -> {} lead(s)", pipelineId, statusId, result.size());
        return result;
    }

    @Override
    public boolean runSalesbot(Long leadId, Long botId) {
        try {
            // TODO: уточнить точный endpoint/тело запуска SalesBot в amoCRM.
            // Здесь использован предполагаемый формат: POST /api/v4/salesbot/run
            // с массивом [{ bot_id, entity_id, entity_type=2 }] (2 = leads).
            SalesbotRunRequest request = new SalesbotRunRequest(botId, leadId, 2);
            JsonArray body = new JsonArray();
            body.add(JsonParser.parseString(gson.toJson(request)));

            webClient.post()
                    .uri("/api/v2/salesbot/run")
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(body.toString())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(b -> Mono.error(new RuntimeException(
                                            "AmoCRM salesbot/run " + clientResponse.statusCode() + ": " + b))))
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully triggered salesbot {} for lead {}", botId, leadId);
            return true;
        } catch (Exception e) {
            log.error("Failed to run salesbot {} for lead {}", botId, leadId, e);
            return false;
        }
    }

    @Override
    public Long createLandingLead(String leadName, String contactName, String phone) {
        return createLead(leadName, contactName, phone, landingPipelineId, landingStatusId);
    }

    @Override
    public Long createLead(String leadName, String contactName, String phone, Long pipelineId, Long statusId) {
        try {
            JsonObject phoneValue = new JsonObject();
            phoneValue.addProperty("value", phone);
            phoneValue.addProperty("enum_code", "WORK");

            JsonArray phoneValues = new JsonArray();
            phoneValues.add(phoneValue);

            JsonObject phoneField = new JsonObject();
            phoneField.addProperty("field_code", "PHONE");
            phoneField.add("values", phoneValues);

            JsonArray contactCustomFields = new JsonArray();
            contactCustomFields.add(phoneField);

            JsonObject contact = new JsonObject();
            contact.addProperty("name", contactName);
            contact.add("custom_fields_values", contactCustomFields);

            JsonArray contacts = new JsonArray();
            contacts.add(contact);

            JsonObject embedded = new JsonObject();
            embedded.add("contacts", contacts);

            JsonObject lead = new JsonObject();
            lead.addProperty("name", leadName);
            lead.addProperty("responsible_user_id", landingResponsibleUserId);
            lead.addProperty("pipeline_id", pipelineId);
            lead.addProperty("status_id", statusId);
            lead.add("_embedded", embedded);

            JsonArray requestBody = new JsonArray();
            requestBody.add(lead);

            String response = webClient.post()
                    .uri("/api/v4/leads/complex")
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            "AmoCRM leads/complex API " + clientResponse.statusCode() + ": " + body))))
                    .bodyToMono(String.class)
                    .block();

            JsonElement parsedResponse = JsonParser.parseString(response);

            // /api/v4/leads/complex может вернуть массив созданных сущностей.
            if (parsedResponse.isJsonArray()) {
                JsonArray createdLeads = parsedResponse.getAsJsonArray();
                if (!createdLeads.isEmpty()) {
                    JsonObject createdLead = createdLeads.get(0).getAsJsonObject();
                    if (createdLead.has("id") && !createdLead.get("id").isJsonNull()) {
                        return createdLead.get("id").getAsLong();
                    }
                }
            }

            // На некоторых эндпоинтах amoCRM возвращает объект с _embedded.
            if (parsedResponse.isJsonObject()) {
                JsonObject jsonResponse = parsedResponse.getAsJsonObject();
                if (jsonResponse.has("_embedded")
                        && jsonResponse.getAsJsonObject("_embedded").has("leads")
                        && jsonResponse.getAsJsonObject("_embedded").getAsJsonArray("leads").size() > 0) {
                    JsonObject createdLead = jsonResponse.getAsJsonObject("_embedded").getAsJsonArray("leads")
                            .get(0).getAsJsonObject();
                    if (createdLead.has("id") && !createdLead.get("id").isJsonNull()) {
                        return createdLead.get("id").getAsLong();
                    }
                }
            }

            throw new RuntimeException("AmoCRM did not return created lead id");
        } catch (Exception e) {
            log.error("Failed to create lead in amoCRM (pipeline={}, status={})", pipelineId, statusId, e);
            throw new RuntimeException("Failed to create lead in amoCRM", e);
        }
    }

    @Override
    public boolean linkCatalogElementsToLead(Long leadId, Long catalogId, Map<Long, Integer> elementIdToQuantity) {
        if (elementIdToQuantity == null || elementIdToQuantity.isEmpty()) {
            return true;
        }
        try {
            JsonArray requestBody = new JsonArray();
            for (Map.Entry<Long, Integer> entry : elementIdToQuantity.entrySet()) {
                JsonObject metadata = new JsonObject();
                metadata.addProperty("quantity", entry.getValue() == null ? 1 : entry.getValue());
                metadata.addProperty("catalog_id", catalogId);

                JsonObject link = new JsonObject();
                link.addProperty("to_entity_id", entry.getKey());
                link.addProperty("to_entity_type", "catalog_elements");
                link.add("metadata", metadata);
                requestBody.add(link);
            }

            webClient.post()
                    .uri("/api/v4/leads/" + leadId + "/link")
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            "AmoCRM leads/link API " + clientResponse.statusCode() + ": " + body))))
                    .bodyToMono(String.class)
                    .block();

            log.info("Linked {} catalog elements to lead {}", elementIdToQuantity.size(), leadId);
            return true;
        } catch (Exception e) {
            log.error("Failed to link catalog elements to lead {}", leadId, e);
            return false;
        }
    }
}
