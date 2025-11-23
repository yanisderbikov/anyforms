package ru.anyforms.service;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.model.CdekWebhook;

@Service
public class CdekWebhookService {
    private static final Logger logger = LoggerFactory.getLogger(CdekWebhookService.class);
    
    private final GoogleSheetsService googleSheetsService;
    private final Gson gson;
    
    // Индексы колонок (0-based: A=0, B=1, ..., I=8, J=9)
    private static final int COLUMN_I_INDEX = 8;  // Колонка I (трекер)
    private static final int COLUMN_J_INDEX = 9;  // Колонка J (статус)
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    public CdekWebhookService(GoogleSheetsService googleSheetsService) {
        this.googleSheetsService = googleSheetsService;
        this.gson = new Gson();
    }

    /**
     * Обрабатывает вебхук от СДЭК
     * @param webhookJson JSON строка с вебхуком
     */
    public void processWebhook(String webhookJson) {
        try {
            logger.info("Получен вебхук от СДЭК: {}", webhookJson);
            
            CdekWebhook webhook = gson.fromJson(webhookJson, CdekWebhook.class);
            
            if (webhook == null) {
                logger.warn("Не удалось распарсить вебхук СДЭК");
                return;
            }
            
            // Обрабатываем только вебхуки типа ORDER_STATUS
            if (!"ORDER_STATUS".equals(webhook.getType())) {
                logger.debug("Пропускаем вебхук типа: {}", webhook.getType());
                return;
            }
            
            CdekWebhook.Attributes attributes = webhook.getAttributes();
            if (attributes == null) {
                logger.warn("Вебхук не содержит атрибутов");
                return;
            }
            
            String cdekNumber = attributes.getCdekNumber();
            if (cdekNumber == null || cdekNumber.trim().isEmpty()) {
                logger.warn("Вебхук не содержит номера заказа СДЭК");
                return;
            }
            
            // Получаем статус из атрибутов
            String statusName = attributes.getName();
            String statusCode = attributes.getCode();
            
            // Формируем строку статуса
            String statusText = statusName != null && !statusName.isEmpty() 
                    ? statusName 
                    : (statusCode != null ? statusCode : "Неизвестный статус");
            
            logger.info("Обработка вебхука для заказа СДЭК: {}, статус: {}", cdekNumber, statusText);
            
            // Ищем строку в таблице по номеру трекера (колонка I) и записываем статус в колонку J
            boolean found = googleSheetsService.findAndWriteCell(
                    sheetName,
                    COLUMN_I_INDEX,  // Ищем в колонке I (трекер)
                    cdekNumber,      // По номеру трекера
                    COLUMN_J_INDEX,  // Записываем в колонку J
                    statusText       // Статус
            );
            
            if (found) {
                logger.info("Статус '{}' успешно записан в таблицу для трекера {}", statusText, cdekNumber);
            } else {
                logger.warn("Не найдена строка с трекером {} в таблице '{}'", cdekNumber, sheetName);
            }
            
        } catch (Exception e) {
            logger.error("Ошибка при обработке вебхука СДЭК: {}", e.getMessage(), e);
        }
    }
}

