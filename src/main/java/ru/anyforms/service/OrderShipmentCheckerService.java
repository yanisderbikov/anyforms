package ru.anyforms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.model.AmoCrmFieldId;
import ru.anyforms.model.AmoLeadStatus;
import ru.anyforms.model.CdekOrderStatus;
import ru.anyforms.util.CdekStatusHelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrderShipmentCheckerService {
    private static final Logger logger = LoggerFactory.getLogger(OrderShipmentCheckerService.class);
    
    private final GoogleSheetsService googleSheetsService;
    private final CdekTrackingService cdekTrackingService;
    private final AmoCrmService amoCrmService;
    
    // Индексы колонок (0-based: A=0, B=1, ..., I=8, J=9, E=4)
    private static final int COLUMN_I_INDEX = 8;  // Колонка I (трекер)
    private static final int COLUMN_J_INDEX = 9;  // Колонка J (статус)
    private static final int COLUMN_E_INDEX = 4;  // Колонка E (ссылка на сделку)
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    // Паттерн для извлечения ID сделки из URL
    private static final Pattern LEAD_ID_PATTERN = Pattern.compile("leads/detail/(\\d+)");

    public OrderShipmentCheckerService(GoogleSheetsService googleSheetsService,
                                      CdekTrackingService cdekTrackingService,
                                      AmoCrmService amoCrmService) {
        this.googleSheetsService = googleSheetsService;
        this.cdekTrackingService = cdekTrackingService;
        this.amoCrmService = amoCrmService;
    }

    /**
     * Проверяет все заказы в таблице на предмет отправки
     * Обрабатывает заказы, у которых:
     * - Колонка J пустая или содержит "CREATED"
     * - Колонка I содержит трекер
     * - Статус в СДЭК более чем RECEIVED_AT_SHIPMENT_WAREHOUSE
     */
    public void checkAllOrdersForShipment() {
        try {
            logger.info("Начало проверки всех заказов на отправку в таблице '{}'", sheetName);
            
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsService.readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty()) {
                logger.info("В таблице '{}' нет строк", sheetName);
                return;
            }
            
            int processedCount = 0;
            int shippedCount = 0;
            
            // Обрабатываем строки начиная со второй (пропускаем заголовок)
            for (int i = 1; i < allRows.size(); i++) {
                List<Object> row = allRows.get(i);
                int rowNumber = i + 1; // Номер строки в таблице (1-based)
                
                // Проверяем условия
                if (shouldProcessRow(row, rowNumber)) {
                    processedCount++;
                    String trackingNumber = googleSheetsService.getCellValue(row, COLUMN_I_INDEX);
                    
                    logger.info("Проверка заказа в строке {}: трекер {}", rowNumber, trackingNumber);
                    
                    // Проверяем статус заказа в СДЭК
                    if (checkAndProcessShippedOrder(row, rowNumber, trackingNumber)) {
                        shippedCount++;
                    }
                }
            }
            
            logger.info("Проверка завершена. Обработано заказов: {}, отправлено: {}", processedCount, shippedCount);
            
        } catch (Exception e) {
            logger.error("Ошибка при проверке заказов на отправку: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Проверяет, нужно ли обрабатывать строку
     * Условия:
     * - Колонка I содержит валидный трекер
     * - Колонка J пустая или содержит статус, который НЕ DELIVERED или NOT_FOUND_OR_DELIVERED
     */
    private boolean shouldProcessRow(List<Object> row, int rowNumber) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        
        String columnI = googleSheetsService.getCellValue(row, COLUMN_I_INDEX);
        String columnJ = googleSheetsService.getCellValue(row, COLUMN_J_INDEX);
        
        // Проверка 1: Колонка I должна содержать валидный трекер
        if (columnI.isEmpty() || !cdekTrackingService.isValidTrackingNumber(columnI)) {
            return false;
        }
        
        // Проверка 2: Колонка J должна быть пустая или содержать статус, который НЕ DELIVERED или NOT_FOUND_OR_DELIVERED
        String columnJUpper = columnJ.toUpperCase().trim();
        if (!columnJ.isEmpty()) {
            if (columnJUpper.equals(CdekOrderStatus.DELIVERED.getCode()) || columnJUpper.equals(CdekOrderStatus.NOT_FOUND_OR_DELIVERED.getCode())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Проверяет статус заказа и обрабатывает его, если статус изменился
     * @return true если заказ был обработан
     */
    private boolean checkAndProcessShippedOrder(List<Object> row, int rowNumber, String trackingNumber) {
        try {
            // Получаем текущий статус из колонки J
            String currentStatus = googleSheetsService.getCellValue(row, COLUMN_J_INDEX);
            String currentStatusUpper = currentStatus.toUpperCase().trim();
            
            // Получаем новый статус из СДЭК
            String newStatusCode = cdekTrackingService.getOrderStatusCode(trackingNumber);
            
            if (newStatusCode == null || newStatusCode.isEmpty()) {
                logger.warn("Не удалось получить статус для трекера {} в строке {}", trackingNumber, rowNumber);
                return false;
            }
            
            // Если заказ не найден или доставлен, записываем статус в таблицу и не обрабатываем дальше
            if ("NOT_FOUND_OR_DELIVERED".equals(newStatusCode)) {
                logger.info("Заказ {} в строке {} не найден или доставлен, записываем статус в таблицу", 
                        trackingNumber, rowNumber);
                writeStatusToTable(trackingNumber, rowNumber, newStatusCode);
                return false;
            }
            
            // Если статус DELIVERED, записываем и не обрабатываем дальше
            if ("DELIVERED".equals(newStatusCode)) {
                logger.info("Заказ {} в строке {} доставлен, записываем статус в таблицу", 
                        trackingNumber, rowNumber);
                writeStatusToTable(trackingNumber, rowNumber, newStatusCode);
                updateAmoCrmStatusIfNeeded(row, rowNumber, trackingNumber, newStatusCode);
                return false;
            }
            
            // Определяем статус заказа
            CdekOrderStatus orderStatus = CdekOrderStatus.fromCode(newStatusCode);
            
            // Нормализуем текущий статус для сравнения
            String normalizedCurrentStatus = currentStatusUpper.isEmpty() ? "" : currentStatusUpper;
            String normalizedNewStatus = newStatusCode.toUpperCase().trim();
            
            // Проверяем, изменился ли статус
            boolean statusChanged = !normalizedCurrentStatus.equals(normalizedNewStatus);
            
            if (statusChanged) {
                logger.info("Статус заказа {} в строке {} изменился: {} -> {}", 
                        trackingNumber, rowNumber, currentStatus, newStatusCode);
                
                // Записываем новый статус в таблицу
                writeStatusToTable(trackingNumber, rowNumber, newStatusCode);
                
                // Обновляем статус в AmoCRM, если нужно
                updateAmoCrmStatusIfNeeded(row, rowNumber, trackingNumber, newStatusCode);
                
                // Если заказ отправлен (более чем RECEIVED_AT_SHIPMENT_WAREHOUSE), обрабатываем отправку
                if (isShipped(orderStatus)) {
                    logger.info("Заказ {} в строке {} отправлен (статус: {}), обрабатываем...", 
                            trackingNumber, rowNumber, newStatusCode);
                    
                    // Обрабатываем отправленный заказ (добавляем трекер, отправляем сообщение, обновляем статус на SENT)
                    processShippedOrder(row, rowNumber, trackingNumber);
                    return true;
                }
            } else {
                logger.debug("Статус заказа {} в строке {} не изменился: {}", 
                        trackingNumber, rowNumber, newStatusCode);
            }
            
        } catch (Exception e) {
            logger.error("Ошибка при проверке статуса заказа {} в строке {}: {}", 
                    trackingNumber, rowNumber, e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Проверяет, является ли статус "отправлен" (более чем RECEIVED_AT_SHIPMENT_WAREHOUSE)
     */
    private boolean isShipped(CdekOrderStatus status) {
        if (status == null || status == CdekOrderStatus.UNKNOWN) {
            return false;
        }
        
        String statusCode = status.getCode();
        
        // Если статус RECEIVED_AT_SHIPMENT_WAREHOUSE или раньше - еще не отправлен
        if (status == CdekOrderStatus.CREATED ||
            status == CdekOrderStatus.ACCEPTED ||
            status == CdekOrderStatus.RECEIVED_AT_SHIPMENT_WAREHOUSE ||
            status == CdekOrderStatus.ACCEPTED_AT_SHIPMENT_WAREHOUSE ||
            status == CdekOrderStatus.DELIVERED_TO_SHIPMENT_WAREHOUSE) {
            return false;
        }
        
        // Проверяем по коду для статуса READY_FOR_SHIPMENT_IN_SENDER_CITY (может не быть в enum)
        if (statusCode != null && statusCode.equals("READY_FOR_SHIPMENT_IN_SENDER_CITY")) {
            return true;
        }
        
        // Все остальные статусы означают, что заказ отправлен
        return status == CdekOrderStatus.RECEIVED_IN_SENDER_CITY ||
               status == CdekOrderStatus.TRANSFERRED_TO_DELIVERY_IN_SENDER_CITY ||
               status == CdekOrderStatus.ARRIVED_IN_RECIPIENT_CITY ||
               status == CdekOrderStatus.TRANSFERRED_TO_DELIVERY_IN_RECIPIENT_CITY ||
               status == CdekOrderStatus.RECEIVED_AT_DELIVERY_WAREHOUSE ||
               status == CdekOrderStatus.ACCEPTED_AT_DELIVERY_WAREHOUSE ||
               status == CdekOrderStatus.DELIVERED_TO_DELIVERY_WAREHOUSE ||
               status == CdekOrderStatus.ACCEPTED_AT_PICKUP_POINT ||
               status == CdekOrderStatus.DELIVERED_TO_PICKUP_POINT ||
               status == CdekOrderStatus.ISSUED_FOR_DELIVERY ||
               status == CdekOrderStatus.DELIVERED ||
               status == CdekOrderStatus.HANDED_TO;
    }
    
    /**
     * Обрабатывает отправленный заказ:
     * - Добавляет трекер в сделку AMO
     * - Отправляет сообщение о том, что заказ отправлен
     */
    private void processShippedOrder(List<Object> row, int rowNumber, String trackingNumber) {
        try {
            // Получаем ссылку на сделку из столбца E
            String dealLink = googleSheetsService.getCellValue(row, COLUMN_E_INDEX);
            
            if (dealLink == null || dealLink.trim().isEmpty()) {
                logger.warn("Не найдена ссылка на сделку в столбце E для трекера {} в строке {}", 
                        trackingNumber, rowNumber);
                return;
            }
            
            // Извлекаем ID сделки из ссылки
            Long leadId = extractLeadIdFromUrl(dealLink);
            if (leadId == null) {
                logger.warn("Не удалось извлечь ID сделки из ссылки: {} для трекера {} в строке {}", 
                        dealLink, trackingNumber, rowNumber);
                return;
            }
            
            logger.info("Обработка отправленного заказа: трекер {}, сделка {}", trackingNumber, leadId);
            
            // Добавляем трекер в CRM
            boolean updated = amoCrmService.updateLeadCustomField(leadId, AmoCrmFieldId.TRACKER.getId(), trackingNumber);
            if (updated) {
                logger.info("Трекер {} успешно добавлен в CRM для сделки {}", trackingNumber, leadId);
            } else {
                logger.error("Не удалось добавить трекер {} в CRM для сделки {}", trackingNumber, leadId);
                return;
            }
            
            // Отправляем сообщение в мессенджер
            String message = "Ваш заказ был отправлен:\n\nТрекер: " + trackingNumber;
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
            
        } catch (Exception e) {
            logger.error("Ошибка при обработке отправленного заказа {} в строке {}: {}", 
                    trackingNumber, rowNumber, e.getMessage(), e);
        }
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
     * Записывает статус в колонку J таблицы для указанного трекера
     */
    private void writeStatusToTable(String trackingNumber, int rowNumber, String statusCode) {
        try {
            // Записываем статус в колонку J (индекс 9) для строки rowNumber
            googleSheetsService.writeCell(sheetName, rowNumber, COLUMN_J_INDEX, statusCode);
            logger.info("Статус '{}' записан в колонку J для трекера {} в строке {}", 
                    statusCode, trackingNumber, rowNumber);
            
            // Обновляем статус доставки в amoCRM (поле 2601105)
            updateDeliveryStatusInAmoCrm(rowNumber, statusCode);
        } catch (Exception e) {
            logger.error("Ошибка при записи статуса в таблицу для трекера {} в строке {}: {}", 
                    trackingNumber, rowNumber, e.getMessage(), e);
        }
    }
    
    /**
     * Обновляет статус доставки в amoCRM (поле 2601105) на основе статуса из Google таблицы
     * @param rowNumber номер строки в таблице (1-based)
     * @param statusText текст статуса для записи в amoCRM
     */
    private void updateDeliveryStatusInAmoCrm(int rowNumber, String statusText) {
        try {
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsService.readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty() || rowNumber > allRows.size()) {
                logger.warn("Не удалось обновить статус доставки в amoCRM: строка {} не найдена в таблице", rowNumber);
                return;
            }
            
            // Получаем строку (rowNumber - 1, так как rowNumber 1-based, а список 0-based)
            List<Object> row = allRows.get(rowNumber - 1);
            
            // Получаем ссылку на сделку из столбца E
            String dealLink = googleSheetsService.getCellValue(row, COLUMN_E_INDEX);
            
            if (dealLink == null || dealLink.trim().isEmpty()) {
                logger.warn("Не найдена ссылка на сделку в столбце E для строки {}, не удалось обновить статус доставки в amoCRM", rowNumber);
                return;
            }
            
            // Извлекаем ID сделки из ссылки
            Long leadId = extractLeadIdFromUrl(dealLink);
            if (leadId == null) {
                logger.warn("Не удалось извлечь ID сделки из ссылки: {} для строки {}, не удалось обновить статус доставки в amoCRM", dealLink, rowNumber);
                return;
            }
            
            // Обновляем поле статуса доставки в amoCRM
            boolean updated = amoCrmService.updateLeadCustomField(leadId, AmoCrmFieldId.DELIVERY_STATUS.getId(), statusText);
            if (updated) {
                logger.info("Статус доставки '{}' успешно обновлен в amoCRM (поле {}) для сделки {} в строке {}", 
                        statusText, AmoCrmFieldId.DELIVERY_STATUS.getId(), leadId, rowNumber);
            } else {
                logger.error("Не удалось обновить статус доставки '{}' в amoCRM (поле {}) для сделки {} в строке {}", 
                        statusText, AmoCrmFieldId.DELIVERY_STATUS.getId(), leadId, rowNumber);
            }
            
        } catch (Exception e) {
            logger.error("Ошибка при обновлении статуса доставки в amoCRM для строки {}: {}", 
                    rowNumber, e.getMessage(), e);
        }
    }
    
    /**
     * Обновляет статус сделки в AmoCRM на основе статуса CDEK, если нужно
     * @param row строка таблицы
     * @param rowNumber номер строки в таблице (1-based)
     * @param trackingNumber номер трекера
     * @param statusCode код статуса CDEK
     */
    private void updateAmoCrmStatusIfNeeded(List<Object> row, int rowNumber, String trackingNumber, String statusCode) {
        try {
            // Получаем ссылку на сделку из столбца E
            String dealLink = googleSheetsService.getCellValue(row, COLUMN_E_INDEX);
            
            if (dealLink == null || dealLink.trim().isEmpty()) {
                logger.warn("Не найдена ссылка на сделку в столбце E для трекера {} в строке {}", 
                        trackingNumber, rowNumber);
                return;
            }
            
            // Извлекаем ID сделки из ссылки
            Long leadId = extractLeadIdFromUrl(dealLink);
            if (leadId == null) {
                logger.warn("Не удалось извлечь ID сделки из ссылки: {} для трекера {} в строке {}", 
                        dealLink, trackingNumber, rowNumber);
                return;
            }
            
            // Определяем статус заказа
            CdekOrderStatus orderStatus = CdekOrderStatus.fromCode(statusCode);
            
            // Определяем, какой статус AmoCRM нужно установить
            AmoLeadStatus targetAmoStatus = null;
            
            // Если статус "доставлен" (можно забрать)
            if (CdekStatusHelper.isDelivered(orderStatus)) {
                targetAmoStatus = AmoLeadStatus.DELIVERED;
            }
            // Если статус "вручен" (клиент забрал)
            else if (CdekStatusHelper.isHandedTo(orderStatus)) {
                targetAmoStatus = AmoLeadStatus.REALIZED;
            }
            // Если статус "принят на доставку" (после Created)
            else if (CdekStatusHelper.isAcceptedForDelivery(orderStatus)) {
                targetAmoStatus = AmoLeadStatus.SENT;
            }
            
            // Обновляем статус в AmoCRM, если нужно
            if (targetAmoStatus != null) {
                boolean statusUpdated = amoCrmService.updateLeadStatus(leadId, targetAmoStatus, null);
                if (statusUpdated) {
                    logger.info("Статус сделки {} успешно обновлен на '{}' ({}) для трекера {} (статус CDEK: {})", 
                            leadId, targetAmoStatus.getDescription(), targetAmoStatus.getStatusId(), trackingNumber, statusCode);
                } else {
                    logger.warn("Не удалось обновить статус сделки {} на '{}' для трекера {} (статус CDEK: {})", 
                            leadId, targetAmoStatus.getDescription(), trackingNumber, statusCode);
                }
            }
            
        } catch (Exception e) {
            logger.error("Ошибка при обновлении статуса AmoCRM для трекера {} в строке {}: {}", 
                    trackingNumber, rowNumber, e.getMessage(), e);
        }
    }
    
}

