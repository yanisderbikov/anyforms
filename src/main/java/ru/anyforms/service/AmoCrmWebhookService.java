package ru.anyforms.service;

public interface AmoCrmWebhookService {
    void processFormDataWebhook(String formData);
    void processJsonWebhook(String jsonBody);
}
