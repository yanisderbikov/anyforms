package ru.anyforms.service.amo;

public interface AmoCrmWebhookService {
    void processFormDataWebhook(String formData);
    void processFormDataSyncOrderWebhook(String formData);
}
