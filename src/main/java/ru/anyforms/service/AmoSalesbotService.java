package ru.anyforms.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.anyforms.model.SalesbotRunRequest;
import ru.anyforms.model.SalesbotRunResponse;

import java.util.List;

/**
 * Сервис для работы с Salesbot API в amoCRM
 */
@Service
public class AmoSalesbotService {
    private WebClient webClient;
    private final Gson gson;
    
    @Value("${amocrm.subdomain}")
    private String subdomain;
    
    @Value("${amocrm.access.token}")
    private String accessToken;

    public AmoSalesbotService() {
        this.gson = new Gson();
    }

    @PostConstruct
    private void init() {
        this.webClient = WebClient.builder()
                .baseUrl("https://" + subdomain + ".amocrm.ru")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Запускает Salesbot для одной задачи
     * 
     * @param botId ID бота, которого нужно запустить
     * @param entityId ID сущности к которой привязан контакт с чатом или самого контакта с чатом
     * @param entityType Тип сущности (1 - контакт, 2 - сделка)
     * @return true если бот успешно запущен, false в противном случае
     */
    public boolean runSalesbot(Long botId, Long entityId, Integer entityType) {
        SalesbotRunRequest request = new SalesbotRunRequest(botId, entityId, entityType);
        return runSalesbot(List.of(request));
    }

    /**
     * Запускает Salesbot для нескольких задач (до 100)
     * 
     * @param requests список задач для запуска
     * @return true если все боты успешно запущены, false в противном случае
     */
    public boolean runSalesbot(List<SalesbotRunRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            System.err.println("Salesbot requests list is empty");
            return false;
        }

        if (requests.size() > 100) {
            System.err.println("Salesbot requests list exceeds maximum of 100 tasks");
            return false;
        }

        try {
            // Преобразуем список запросов в JSON массив
            JsonArray requestsArray = new JsonArray();
            for (SalesbotRunRequest request : requests) {
                if (request.getBotId() == null || request.getEntityId() == null || request.getEntityType() == null) {
                    System.err.println("Invalid salesbot request: botId, entityId and entityType must not be null");
                    continue;
                }
                requestsArray.add(gson.toJsonTree(request));
            }

            if (requestsArray.size() == 0) {
                System.err.println("No valid salesbot requests to send");
                return false;
            }

            String url = "/api/v2/salesbot/run";
            String response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(requestsArray.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Парсим ответ
            if (response != null && !response.trim().isEmpty()) {
                SalesbotRunResponse salesbotResponse = gson.fromJson(response, SalesbotRunResponse.class);
                if (salesbotResponse != null && Boolean.TRUE.equals(salesbotResponse.getSuccess())) {
                    System.out.println("Successfully started salesbot for " + requestsArray.size() + " task(s)");
                    return true;
                } else {
                    System.err.println("Salesbot API returned success=false");
                    return false;
                }
            }

            System.err.println("Empty response from salesbot API");
            return false;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.ACCEPTED) {
                // Status Code 202 означает успешный запрос
                System.out.println("Successfully started salesbot (HTTP 202 Accepted)");
                return true;
            }
            System.err.println("Failed to run salesbot: HTTP " + e.getStatusCode() + " - " + e.getMessage());
            if (e.getResponseBodyAsString() != null) {
                System.err.println("Response body: " + e.getResponseBodyAsString());
            }
            return false;
        } catch (Exception e) {
            System.err.println("Failed to run salesbot: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Запускает Salesbot для контакта
     * 
     * @param botId ID бота
     * @param contactId ID контакта
     * @return true если бот успешно запущен, false в противном случае
     */
    public boolean runSalesbotForContact(Long botId, Long contactId) {
        return runSalesbot(botId, contactId, 1); // 1 - контакт
    }

    /**
     * Запускает Salesbot для сделки
     * 
     * @param botId ID бота
     * @param leadId ID сделки
     * @return true если бот успешно запущен, false в противном случае
     */
    public boolean runSalesbotForLead(Long botId, Long leadId) {
        return runSalesbot(botId, leadId, 2); // 2 - сделка
    }
}


