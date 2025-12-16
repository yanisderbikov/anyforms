package ru.anyforms.service;

public interface AmoCrmWebhookService {
    void processFormDataWebhook(String formData);
    void processFormDataSyncOrderWebhook(String formData);
}
