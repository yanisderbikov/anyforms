package ru.anyforms.service.impl;

import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.anyforms.model.CdekWebhook;
import ru.anyforms.service.CdekWebhookService;
import ru.anyforms.service.DeliveryProcessor;


@Log4j2
@Service
class CdekWebhookServiceImpl implements CdekWebhookService {

    private final DeliveryProcessor deliveryProcessor;
    private final Gson gson;

    public CdekWebhookServiceImpl(DeliveryProcessor deliveryProcessor) {
        this.deliveryProcessor = deliveryProcessor;
        this.gson = new Gson();
    }

    /**
     * Обрабатывает вебхук от СДЭК
     * @param webhookJson JSON строка с вебхуком
     */
    public void processWebhook(String webhookJson) {
        try {
            log.info("Получен вебхук от СДЭК: {}", webhookJson);
            
            CdekWebhook webhook = gson.fromJson(webhookJson, CdekWebhook.class);
            
            if (webhook == null) {
                log.warn("Не удалось распарсить вебхук СДЭК");
                return;
            }
            
            // Обрабатываем только вебхуки типа ORDER_STATUS
            if (!"ORDER_STATUS".equals(webhook.getType())) {
                log.debug("Пропускаем вебхук типа: {}", webhook.getType());
                return;
            }
            
            CdekWebhook.Attributes attributes = webhook.getAttributes();
            if (attributes == null) {
                log.warn("Вебхук не содержит атрибутов");
                return;
            }
            
            String cdekNumber = attributes.getCdekNumber();
            if (cdekNumber == null || cdekNumber.trim().isEmpty()) {
                log.warn("Вебхук не содержит номера заказа СДЭК");
                return;
            }
            
            // Получаем статус из атрибутов
            String statusName = attributes.getName();
            String statusCode = attributes.getCode();
            
            // Формируем строку статуса
            String statusText = statusName != null && !statusName.isEmpty() 
                    ? statusName 
                    : (statusCode != null ? statusCode : "Неизвестный статус");

            deliveryProcessor.updateStatus(cdekNumber, statusText);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке вебхука СДЭК: {}", e.getMessage(), e);
        }
    }
}

