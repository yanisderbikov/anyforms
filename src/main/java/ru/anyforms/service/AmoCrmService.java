package ru.anyforms.service;

import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoLead;
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

            System.out.println("Successfully updated status for lead " + leadId + " to status " + statusId);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to update lead status in amoCRM: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
