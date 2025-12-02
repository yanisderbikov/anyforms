package ru.anyforms.util;

import ru.anyforms.model.AmoWebhook;
import ru.anyforms.util.FormDataParser;
import com.google.gson.Gson;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebhookParserService {
    private final Gson gson;

    public WebhookParserService() {
        this.gson = new Gson();
    }

    /**
     * Парсит JSON webhook в объект AmoWebhook
     */
    public AmoWebhook parseJsonWebhook(String jsonBody) {
        return gson.fromJson(jsonBody, AmoWebhook.class);
    }

    /**
     * Парсит form-data webhook в Map структуру
     */
    public Map<String, Object> parseFormDataWebhook(String formData) {
        return FormDataParser.parse(formData);
    }

    /**
     * Извлекает секцию leads из распарсенного form-data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractLeadsFromFormData(Map<String, Object> parsed) {
        return (Map<String, Object>) parsed.get("leads");
    }
}

