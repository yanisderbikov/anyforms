package ru.anyforms.service.impl;

import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoCrmFieldId;
import ru.anyforms.model.AmoLead;
import ru.anyforms.model.AmoLeadStatus;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Deprecated
@Service
public class AmoCrmService {
    private WebClient webClient;
    private final Gson gson;
    
    @Value("${amocrm.subdomain:hairdoskeels38}")
    private String subdomain;
    
    @Value("${amocrm.access.token:}")
    private String accessToken;

    public AmoCrmService() {
        this.gson = new Gson();
    }

    @jakarta.annotation.PostConstruct
    private void init() {
        this.webClient = WebClient.builder()
                .baseUrl("https://" + subdomain + ".amocrm.ru")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

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
                String phoneValue = contact.getCustomFieldValue(AmoCrmFieldId.PHONE.getId());
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

    /**
     * Обновляет статус сделки в amoCRM
     * @param leadId ID сделки
     * @param status статус из enum AmoLeadStatus
     * @param pipelineId ID воронки (опционально, если null - используется текущая воронка сделки)
     * @return true если статус успешно обновлен, false в противном случае
     */
    public boolean updateLeadStatus(Long leadId, AmoLeadStatus status, Long pipelineId) {
        if (status == null || status.getStatusId() == null) {
            System.err.println("Status ID is not configured for status: " + (status != null ? status.getDescription() : "null"));
            return false;
        }
        return updateLeadStatus(leadId, status.getStatusId(), pipelineId);
    }

    /**
     * Обновляет статус сделки в amoCRM
     * @param leadId ID сделки
     * @param statusId ID нового статуса
     * @param pipelineId ID воронки (опционально, если null - используется текущая воронка сделки)
     * @return true если статус успешно обновлен, false в противном случае
     */
    public boolean updateLeadStatus(Long leadId, Long statusId, Long pipelineId) {
        try {
            // Если pipelineId не указан, получаем текущую воронку сделки
            if (pipelineId == null) {
                AmoLead lead = getLead(leadId);
                if (lead == null || lead.getPipelineId() == null) {
                    System.err.println("Failed to get pipeline ID for lead " + leadId);
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
            System.out.println("Successfully updated status for lead " + leadId + " to " + statusDescription);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to update lead status in amoCRM: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Обновляет кастомное поле сделки в amoCRM
     * @param leadId ID сделки
     * @param fieldId ID кастомного поля
     * @param value значение для установки
     * @return true если поле успешно обновлено, false в противном случае
     */
    public boolean updateLeadCustomField(Long leadId, Long fieldId, String value) {
        try {
            // Получаем текущую сделку для сохранения других полей
            AmoLead lead = getLead(leadId);
            if (lead == null) {
                System.err.println("Failed to get lead " + leadId);
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

            System.out.println("Successfully updated custom field " + fieldId + " for lead " + leadId + " with value: " + value);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to update custom field in amoCRM: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Отправляет сообщение в последний мессенджер сделки
     * @param leadId ID сделки
     * @param message текст сообщения
     * @return true если сообщение успешно отправлено, false в противном случае
     */
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
                System.err.println("Chats endpoint returned 404 for lead " + leadId + ", trying alternative method");
                // Пробуем получить чаты через контакт как fallback
                Long contactId = getContactIdFromLead(leadId);
                if (contactId != null) {
                    return sendMessageViaUnsorted(contactId, message);
                }
                return false;
            }

            if (chatsResponse == null || chatsResponse.trim().isEmpty()) {
                System.err.println("Failed to get chats for lead " + leadId + ", trying fallback method");
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
                System.err.println("No chats found for lead " + leadId);
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
                System.err.println("Failed to get chat ID for lead " + leadId);
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

            System.out.println("Successfully sent message to chat " + chatId + " for lead " + leadId);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send message in amoCRM: " + e.getMessage());
            e.printStackTrace();
            // Пробуем fallback метод
            try {
                Long contactId = getContactIdFromLead(leadId);
                if (contactId != null) {
                    return sendMessageViaUnsorted(contactId, message);
                }
            } catch (Exception ex) {
                // Игнорируем ошибку fallback
            }
            return false;
        }
    }

    /**
     * Обновляет несколько полей сделки одновременно (price и кастомные поля)
     * @param leadId ID сделки
     * @param price цена (бюджет), может быть null
     * @param customFields Map с ID кастомных полей и их значениями
     * @return true если поля успешно обновлены, false в противном случае
     */
    public boolean updateLeadFields(Long leadId, Long price, java.util.Map<Long, String> customFields) {
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
                for (java.util.Map.Entry<Long, String> entry : customFields.entrySet()) {
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

            System.out.println("Successfully updated fields for lead " + leadId);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to update lead fields in amoCRM: " + e.getMessage());
            e.printStackTrace();
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

            System.out.println("Successfully sent message via unsorted API for contact " + contactId);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send message via unsorted API: " + e.getMessage());
            return false;
        }
    }

    /**
     * Добавляет примечание к сделке в amoCRM
     * @param leadId ID сделки
     * @param noteText текст примечания
     * @return true если примечание успешно добавлено, false в противном случае
     */
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

            System.out.println("Successfully added note to lead " + leadId);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to add note to lead in amoCRM: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Получает товары из сделки через параметр with=catalog_elements и каталог
     * @param leadId ID сделки
     * @return список товаров или пустой список, если товаров нет
     */
    public java.util.List<ru.anyforms.model.AmoProduct> getLeadProducts(Long leadId) {
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
            java.util.List<ru.anyforms.model.AmoProduct> products = new java.util.ArrayList<>();
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
            Long catalogId = 12983L; // ID каталога из URL
            java.util.List<Long> productIds = new java.util.ArrayList<>(productMetadataMap.keySet());
            
            // AmoCRM API позволяет получить до 250 элементов за раз
            // Разбиваем на батчи по 250 элементов
            int batchSize = 250;
            for (int i = 0; i < productIds.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, productIds.size());
                java.util.List<Long> batch = productIds.subList(i, endIndex);
                
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
                                        ru.anyforms.model.AmoProduct product = new ru.anyforms.model.AmoProduct();
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
            System.err.println("Failed to get products from lead " + leadId + ": " + e.getMessage());
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }
}
