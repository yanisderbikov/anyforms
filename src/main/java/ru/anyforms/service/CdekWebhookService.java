package ru.anyforms.service;

public interface CdekWebhookService {
    void processWebhook(String webhookJson);
}
