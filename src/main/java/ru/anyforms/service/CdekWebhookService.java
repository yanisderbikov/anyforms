package ru.anyforms.service;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.model.AmoLeadStatus;
import ru.anyforms.model.CdekOrderStatus;
import ru.anyforms.model.CdekWebhook;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CdekWebhookService {
    private static final Logger logger = LoggerFactory.getLogger(CdekWebhookService.class);
    
    private final GoogleSheetsService googleSheetsService;
    private final AmoCrmService amoCrmService;
    private final Gson gson;
    
    // Индексы колонок (0-based: A=0, B=1, ..., I=8, J=9, E=4)
    private static final int COLUMN_I_INDEX = 8;  // Колонка I (трекер)
    private static final int COLUMN_J_INDEX = 9;  // Колонка J (статус)
    private static final int COLUMN_E_INDEX = 4;  // Колонка E (ссылка на сделку)
    
    // ID кастомного поля в CRM для трекера
    private static final Long TRACKER_FIELD_ID = 2348069L;
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    // Паттерн для извлечения ID сделки из URL
    private static final Pattern LEAD_ID_PATTERN = Pattern.compile("leads/detail/(\\d+)");

    public CdekWebhookService(GoogleSheetsService googleSheetsService, AmoCrmService amoCrmService) {
        this.googleSheetsService = googleSheetsService;
        this.amoCrmService = amoCrmService;
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
            
            // Определяем статус заказа
            CdekOrderStatus orderStatus = CdekOrderStatus.fromCode(statusCode);
            
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
                
                // Проверяем, является ли статус "принят на доставку" (после Created)
                // Это может быть ACCEPTED, RECEIVED_AT_SHIPMENT_WAREHOUSE и т.д.
                if (isAcceptedForDelivery(orderStatus)) {
                    logger.info("Заказ {} принят на доставку, обрабатываем...", cdekNumber);
                    processAcceptedForDelivery(cdekNumber);
                }
                // Проверяем, является ли статус "доставлен" (можно забрать)
                else if (isDelivered(orderStatus)) {
                    logger.info("Заказ {} доставлен, обрабатываем...", cdekNumber);
                    processDelivered(cdekNumber);
                }
                // Проверяем, является ли статус "вручен" (клиент забрал)
                else if (isHandedTo(orderStatus)) {
                    logger.info("Заказ {} вручен клиенту, обрабатываем...", cdekNumber);
                    processHandedTo(cdekNumber);
                }
            } else {
                logger.warn("Не найдена строка с трекером {} в таблице '{}'", cdekNumber, sheetName);
            }
            
        } catch (Exception e) {
            logger.error("Ошибка при обработке вебхука СДЭК: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Проверяет, является ли статус "принят на доставку" (после Created)
     */
    private boolean isAcceptedForDelivery(CdekOrderStatus status) {
        // Статусы, которые означают "принят на доставку" после Created
        return status == CdekOrderStatus.ACCEPTED ||
               status == CdekOrderStatus.RECEIVED_AT_SHIPMENT_WAREHOUSE ||
               status == CdekOrderStatus.ACCEPTED_AT_SHIPMENT_WAREHOUSE ||
               status == CdekOrderStatus.DELIVERED_TO_SHIPMENT_WAREHOUSE ||
               status == CdekOrderStatus.RECEIVED_IN_SENDER_CITY ||
               status == CdekOrderStatus.TRANSFERRED_TO_DELIVERY_IN_SENDER_CITY;
    }
    
    /**
     * Проверяет, является ли статус "доставлен" (можно забрать)
     */
    private boolean isDelivered(CdekOrderStatus status) {
        return status == CdekOrderStatus.DELIVERED ||
               status == CdekOrderStatus.DELIVERED_TO_PICKUP_POINT ||
               status == CdekOrderStatus.ACCEPTED_AT_PICKUP_POINT;
    }
    
    /**
     * Проверяет, является ли статус "вручен" (клиент забрал)
     */
    private boolean isHandedTo(CdekOrderStatus status) {
        return status == CdekOrderStatus.HANDED_TO;
    }
    
    /**
     * Общий метод для обработки заказа по трекеру
     * Находит сделку в Google таблице и выполняет переданное действие
     * @param trackerNumber номер трекера
     * @param action действие для выполнения после нахождения сделки (leadId, trackerNumber)
     * @param errorContext контекст ошибки для логирования
     */
    private void processOrderByTracker(String trackerNumber, BiConsumer<Long, String> action, String errorContext) {
        try {
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsService.readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty()) {
                logger.warn("Таблица '{}' пуста", sheetName);
                return;
            }
            
            // Ищем строку с нужным трекером (начиная со второй строки, пропуская заголовок)
            for (int i = 1; i < allRows.size(); i++) {
                List<Object> row = allRows.get(i);
                String cellValue = googleSheetsService.getCellValue(row, COLUMN_I_INDEX);
                
                // Очищаем значения для сравнения (убираем пробелы и дефисы)
                String cleanedSearchValue = trackerNumber.trim().replaceAll("[\\s-]", "");
                String cleanedCellValue = cellValue.trim().replaceAll("[\\s-]", "");
                
                if (cleanedCellValue.equals(cleanedSearchValue)) {
                    // Нашли строку с трекером
                    logger.info("Найдена строка с трекером {} в таблице", trackerNumber);
                    
                    // Получаем ссылку на сделку из столбца E
                    String dealLink = googleSheetsService.getCellValue(row, COLUMN_E_INDEX);
                    
                    if (dealLink == null || dealLink.trim().isEmpty()) {
                        logger.warn("Не найдена ссылка на сделку в столбце E для трекера {}", trackerNumber);
                        return;
                    }
                    
                    // Извлекаем ID сделки из ссылки
                    Long leadId = extractLeadIdFromUrl(dealLink);
                    if (leadId == null) {
                        logger.warn("Не удалось извлечь ID сделки из ссылки: {}", dealLink);
                        return;
                    }
                    
                    logger.info("Извлечен ID сделки: {} из ссылки: {}", leadId, dealLink);
                    
                    // Выполняем переданное действие
                    action.accept(leadId, trackerNumber);
                    
                    return;
                }
            }
            
            logger.warn("Не найдена строка с трекером {} в таблице '{}'", trackerNumber, sheetName);
            
        } catch (Exception e) {
            logger.error("Ошибка при обработке заказа ({}) для трекера {}: {}", 
                    errorContext, trackerNumber, e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает заказ, который был принят на доставку
     * Находит трекер и сделку в Google таблице, обновляет CRM и отправляет сообщение
     */
    private void processAcceptedForDelivery(String trackerNumber) {
        processOrderByTracker(trackerNumber, (leadId, tracker) -> {
            // Добавляем трекер в CRM под id 2348069
            boolean updated = amoCrmService.updateLeadCustomField(leadId, TRACKER_FIELD_ID, tracker);
            if (updated) {
                logger.info("Трекер {} успешно добавлен в CRM для сделки {}", tracker, leadId);
            } else {
                logger.error("Не удалось добавить трекер {} в CRM для сделки {}", tracker, leadId);
                return;
            }
            
            // Отправляем сообщение в мессенджер
            String message = "Ваш заказ был отправлен:\n\nТрекер: " + tracker;
            boolean messageSent = amoCrmService.sendMessageToContact(leadId, message);
            if (messageSent) {
                logger.info("Сообщение успешно отправлено в мессенджер для сделки {}", leadId);
            } else {
                logger.warn("Не удалось отправить сообщение в мессенджер для сделки {}", leadId);
            }
            
            // Обновляем статус сделки на "отправлен"
            boolean statusUpdated = amoCrmService.updateLeadStatus(leadId, AmoLeadStatus.SENT, null);
            if (statusUpdated) {
                logger.info("Статус сделки {} успешно обновлен на '{}' ({})", 
                        leadId, AmoLeadStatus.SENT.getDescription(), AmoLeadStatus.SENT.getStatusId());
            } else {
                logger.warn("Не удалось обновить статус сделки {} на '{}'", 
                        leadId, AmoLeadStatus.SENT.getDescription());
            }
        }, "принят на доставку");
    }
    
    /**
     * Извлекает ID сделки из URL
     * @param url ссылка на сделку (например, https://hairdoskeels38.amocrm.ru/leads/detail/123456)
     * @return ID сделки или null, если не удалось извлечь
     */
    private Long extractLeadIdFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        Matcher matcher = LEAD_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.warn("Не удалось преобразовать ID сделки в число: {}", matcher.group(1));
                return null;
            }
        }
        
        // Пробуем найти ID в конце URL, если паттерн не сработал
        String[] parts = url.split("/");
        if (parts.length > 0) {
            try {
                String lastPart = parts[parts.length - 1];
                // Убираем возможные параметры запроса
                if (lastPart.contains("?")) {
                    lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                }
                return Long.parseLong(lastPart);
            } catch (NumberFormatException e) {
                // Игнорируем, пробуем другой способ
            }
        }
        
        return null;
    }
    
    /**
     * Обрабатывает заказ, который был доставлен (можно забрать)
     * Находит сделку в Google таблице, обновляет статус в CRM и отправляет сообщение
     */
    private void processDelivered(String trackerNumber) {
        processOrderByTracker(trackerNumber, (leadId, tracker) -> {
            // Отправляем сообщение в мессенджер о доставке
            String message = "Ваша посылка приехала и готова к получению!\n\nТрекер: " + tracker;
            boolean messageSent = amoCrmService.sendMessageToContact(leadId, message);
            if (messageSent) {
                logger.info("Сообщение о доставке успешно отправлено в мессенджер для сделки {}", leadId);
            } else {
                logger.warn("Не удалось отправить сообщение о доставке в мессенджер для сделки {}", leadId);
            }
            
            // Обновляем статус сделки на "доставлен"
            boolean statusUpdated = amoCrmService.updateLeadStatus(leadId, AmoLeadStatus.DELIVERED, null);
            if (statusUpdated) {
                logger.info("Статус сделки {} успешно обновлен на '{}' ({})", 
                        leadId, AmoLeadStatus.DELIVERED.getDescription(), AmoLeadStatus.DELIVERED.getStatusId());
            } else {
                logger.warn("Не удалось обновить статус сделки {} на '{}'", 
                        leadId, AmoLeadStatus.DELIVERED.getDescription());
            }
        }, "доставлен");
    }
    
    /**
     * Обрабатывает заказ, который был вручен клиенту
     * Находит сделку в Google таблице и обновляет статус в CRM
     */
    private void processHandedTo(String trackerNumber) {
        processOrderByTracker(trackerNumber, (leadId, tracker) -> {
            // Обновляем статус сделки на "реализовано"
            boolean statusUpdated = amoCrmService.updateLeadStatus(leadId, AmoLeadStatus.REALIZED, null);
            if (statusUpdated) {
                logger.info("Статус сделки {} успешно обновлен на '{}' ({})", 
                        leadId, AmoLeadStatus.REALIZED.getDescription(), AmoLeadStatus.REALIZED.getStatusId());
            } else {
                logger.warn("Не удалось обновить статус сделки {} на '{}'", 
                        leadId, AmoLeadStatus.REALIZED.getDescription());
            }
        }, "вручен клиенту");
    }
}

