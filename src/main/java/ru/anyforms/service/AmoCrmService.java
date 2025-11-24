package ru.anyforms.service;

import ru.anyforms.model.AmoContact;
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
            if (jsonObject.has("_embedded")) {
                JsonObject embedded = jsonObject.getAsJsonObject("_embedded");
                if (embedded.has("contacts")) {
                    var contacts = embedded.getAsJsonArray("contacts");
                    if (contacts != null && contacts.size() > 0) {
                        return gson.fromJson(contacts.get(0), AmoContact.class);
                    }
                }
            }
            // Try to parse as direct object
            return gson.fromJson(jsonObject, AmoContact.class);
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
     * Отправляет сообщение в последний мессенджер контакта, связанного со сделкой
     * @param leadId ID сделки
     * @param message текст сообщения
     * @return true если сообщение успешно отправлено, false в противном случае
     */
    public boolean sendMessageToContact(Long leadId, String message) {
        try {
            // Получаем ID контакта из сделки
            Long contactId = getContactIdFromLead(leadId);
            if (contactId == null) {
                System.err.println("Failed to get contact ID from lead " + leadId);
                return false;
            }

            // Получаем список чатов контакта
            String chatsUrl = "/api/v4/chats?contact_id=" + contactId;
            String chatsResponse = webClient.get()
                    .uri(chatsUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (chatsResponse == null || chatsResponse.trim().isEmpty()) {
                System.err.println("Failed to get chats for contact " + contactId);
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
                System.err.println("No chats found for contact " + contactId);
                // Пробуем отправить через unsorted API как fallback
                return sendMessageViaUnsorted(contactId, message);
            }

            // Берем последний чат (обычно это последний активный)
            JsonObject lastChat = chatsArray.get(chatsArray.size() - 1).getAsJsonObject();
            Long chatId = null;
            if (lastChat.has("id") && !lastChat.get("id").isJsonNull()) {
                chatId = lastChat.get("id").getAsLong();
            }

            if (chatId == null) {
                System.err.println("Failed to get chat ID for contact " + contactId);
                return sendMessageViaUnsorted(contactId, message);
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

            System.out.println("Successfully sent message to chat " + chatId + " for contact " + contactId + " (lead " + leadId + ")");
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
}
