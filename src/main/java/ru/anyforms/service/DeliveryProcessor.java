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

/**
 * Сервис для обработки статусов доставки и обновления AmoCRM и Google Sheets
 */
@Service
public class DeliveryProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryProcessor.class);
    
    private final GoogleSheetsService googleSheetsService;
    private final AmoCrmService amoCrmService;
    private final OrderService orderService;
    
    // Индексы колонок (0-based: A=0, B=1, ..., I=8, J=9, E=4)
    private static final int COLUMN_I_INDEX = 8;  // Колонка I (трекер)
    private static final int COLUMN_J_INDEX = 9;  // Колонка J (статус)
    private static final int COLUMN_E_INDEX = 4;  // Колонка E (ссылка на сделку)
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    // Паттерн для извлечения ID сделки из URL
    private static final Pattern LEAD_ID_PATTERN = Pattern.compile("leads/detail/(\\d+)");

    public DeliveryProcessor(GoogleSheetsService googleSheetsService, AmoCrmService amoCrmService, OrderService orderService) {
        this.googleSheetsService = googleSheetsService;
        this.amoCrmService = amoCrmService;
        this.orderService = orderService;
    }

    /**
     * Извлекает ID сделки из URL
     * @param url ссылка на сделку (например, https://hairdoskeels38.amocrm.ru/leads/detail/123456)
     * @return ID сделки или null, если не удалось извлечь
     */
    public Long extractLeadIdFromUrl(String url) {
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
     * Обновляет статус в таблице и в AmoCRM одновременно (по трекеру)
     * @param trackerNumber номер трекера для поиска строки в таблице
     * @param statusText текст статуса для записи
     */
    public void updateStatusInTableAndAmoCrm(String trackerNumber, String statusText) {
        try {
            // Сначала обновляем в таблице
            boolean found = googleSheetsService.findAndWriteCell(
                    sheetName,
                    COLUMN_I_INDEX,  // Ищем в колонке I (трекер)
                    trackerNumber,   // По номеру трекера
                    COLUMN_J_INDEX,  // Записываем в колонку J
                    statusText       // Статус
            );

            if (!found) {
                logger.warn("Не найдена строка с трекером {} в таблице '{}', не удалось обновить статус", trackerNumber, sheetName);
                return;
            }

            logger.info("Статус '{}' успешно записан в таблицу для трекера {}", statusText, trackerNumber);

            // Теперь обновляем в AmoCRM
            updateDeliveryStatusInAmoCrmByTracker(trackerNumber, statusText);

        } catch (Exception e) {
            logger.error("Ошибка при обновлении статуса в таблице и AmoCRM для трекера {}: {}", 
                    trackerNumber, e.getMessage(), e);
        }
    }

    /**
     * Обновляет статус в таблице и в AmoCRM одновременно (по номеру строки)
     * @param rowNumber номер строки в таблице (1-based)
     * @param statusText текст статуса для записи
     */
    public void updateStatusInTableAndAmoCrm(int rowNumber, String statusText) {
        try {
            // Сначала обновляем в таблице
            googleSheetsService.writeCell(sheetName, rowNumber, COLUMN_J_INDEX, statusText);
            logger.info("Статус '{}' записан в колонку J для строки {}", statusText, rowNumber);

            // Теперь обновляем в AmoCRM
            updateDeliveryStatusInAmoCrmByRowNumber(rowNumber, statusText);

        } catch (Exception e) {
            logger.error("Ошибка при обновлении статуса в таблице и AmoCRM для строки {}: {}", 
                    rowNumber, e.getMessage(), e);
        }
    }

    /**
     * Обновляет статус доставки в amoCRM (поле 2601105) на основе статуса из Google таблицы (по трекеру)
     * @param trackerNumber номер трекера для поиска строки в таблице
     * @param statusText текст статуса для записи в amoCRM
     */
    private void updateDeliveryStatusInAmoCrmByTracker(String trackerNumber, String statusText) {
        try {
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsService.readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty()) {
                logger.warn("Таблица '{}' пуста, не удалось обновить статус доставки в amoCRM", sheetName);
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
                    // Получаем ссылку на сделку из столбца E
                    String dealLink = googleSheetsService.getCellValue(row, COLUMN_E_INDEX);
                    
                    if (dealLink == null || dealLink.trim().isEmpty()) {
                        logger.warn("Не найдена ссылка на сделку в столбце E для трекера {}, не удалось обновить статус доставки в amoCRM", trackerNumber);
                        return;
                    }
                    
                    // Извлекаем ID сделки из ссылки
                    Long leadId = extractLeadIdFromUrl(dealLink);
                    if (leadId == null) {
                        logger.warn("Не удалось извлечь ID сделки из ссылки: {} для трекера {}, не удалось обновить статус доставки в amoCRM", dealLink, trackerNumber);
                        return;
                    }
                    
                    // Обновляем поле статуса доставки в amoCRM
                    boolean updated = amoCrmService.updateLeadCustomField(leadId, AmoCrmFieldId.DELIVERY_STATUS.getId(), statusText);
                    if (updated) {
                        logger.info("Статус доставки '{}' успешно обновлен в amoCRM (поле {}) для сделки {} (трекер {})", 
                                statusText, AmoCrmFieldId.DELIVERY_STATUS.getId(), leadId, trackerNumber);
                    } else {
                        logger.error("Не удалось обновить статус доставки '{}' в amoCRM (поле {}) для сделки {} (трекер {})", 
                                statusText, AmoCrmFieldId.DELIVERY_STATUS.getId(), leadId, trackerNumber);
                    }
                    
                    // Обновляем статус в БД
                    orderService.updateDeliveryStatus(leadId, statusText);
                    
                    return;
                }
            }
            
            logger.warn("Не найдена строка с трекером {} в таблице '{}', не удалось обновить статус доставки в amoCRM", trackerNumber, sheetName);
            
        } catch (Exception e) {
            logger.error("Ошибка при обновлении статуса доставки в amoCRM для трекера {}: {}", 
                    trackerNumber, e.getMessage(), e);
        }
    }

    /**
     * Обновляет статус доставки в amoCRM (поле 2601105) на основе статуса из Google таблицы (по номеру строки)
     * @param rowNumber номер строки в таблице (1-based)
     * @param statusText текст статуса для записи в amoCRM
     */
    private void updateDeliveryStatusInAmoCrmByRowNumber(int rowNumber, String statusText) {
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
            
            // Обновляем статус в БД
            orderService.updateDeliveryStatus(leadId, statusText);
            
        } catch (Exception e) {
            logger.error("Ошибка при обновлении статуса доставки в amoCRM для строки {}: {}", 
                    rowNumber, e.getMessage(), e);
        }
    }

    /**
     * Обновляет статус сделки в AmoCRM на основе статуса CDEK, если нужно
     * Также обновляет статус доставки в AmoCRM (поле 2601105)
     * @param leadId ID сделки
     * @param trackingNumber номер трекера
     * @param statusCode код статуса CDEK
     */
    public void updateAmoCrmStatusIfNeeded(Long leadId, String trackingNumber, String statusCode) {
        try {
            // Сначала обновляем поле статуса доставки в AmoCRM
            boolean deliveryStatusUpdated = amoCrmService.updateLeadCustomField(leadId, AmoCrmFieldId.DELIVERY_STATUS.getId(), statusCode);
            if (deliveryStatusUpdated) {
                logger.info("Статус доставки '{}' успешно обновлен в amoCRM (поле {}) для сделки {} (трекер {})", 
                        statusCode, AmoCrmFieldId.DELIVERY_STATUS.getId(), leadId, trackingNumber);
            } else {
                logger.error("Не удалось обновить статус доставки '{}' в amoCRM (поле {}) для сделки {} (трекер {})", 
                        statusCode, AmoCrmFieldId.DELIVERY_STATUS.getId(), leadId, trackingNumber);
            }
            
            // Обновляем статус в БД
            orderService.updateDeliveryStatus(leadId, statusCode);

            // Определяем статус заказа
            CdekOrderStatus orderStatus = CdekOrderStatus.fromCode(statusCode);
            
            // Определяем, какой статус AmoCRM нужно установить
            AmoLeadStatus targetAmoStatus = null;
            
            // Если статус "доставлен" (можно забрать)
            if (CdekStatusHelper.isReadyToPickUp(orderStatus)) {
                targetAmoStatus = AmoLeadStatus.DELIVERED;
            }
            // Если статус "вручен" (клиент забрал)
            else if (CdekStatusHelper.isDelivered(orderStatus)) {
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
            logger.error("Ошибка при обновлении статуса AmoCRM для трекера {}: {}", 
                    trackingNumber, e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает заказ, который был принят на доставку
     * Находит трекер и сделку в Google таблице, обновляет CRM и отправляет сообщение
     * @param trackerNumber номер трекера
     * @param leadId ID сделки (если уже известен, иначе null - будет найден по трекеру)
     */
    public void processAcceptedForDelivery(String trackerNumber, Long leadId) {
        if (leadId == null) {
            leadId = findLeadIdByTracker(trackerNumber);
            if (leadId == null) {
                logger.warn("Не удалось найти ID сделки для трекера {}", trackerNumber);
                return;
            }
        }
        
        try {
            // Добавляем трекер в CRM
            boolean updated = amoCrmService.updateLeadCustomField(leadId, AmoCrmFieldId.TRACKER.getId(), trackerNumber);
            if (updated) {
                logger.info("Трекер {} успешно добавлен в CRM для сделки {}", trackerNumber, leadId);
            } else {
                logger.error("Не удалось добавить трекер {} в CRM для сделки {}", trackerNumber, leadId);
                return;
            }
            
            // Обновляем трекер в БД
            orderService.setTrackerForOrder(leadId, trackerNumber);
            
            // Отправляем сообщение в мессенджер
//            String message = "Ваш заказ был отправлен:\n\nТрекер: " + trackerNumber;
//            boolean messageSent = amoCrmService.sendMessageToContact(leadId, message);
//            if (messageSent) {
//                logger.info("Сообщение успешно отправлено в мессенджер для сделки {}", leadId);
//            } else {
//                logger.warn("Не удалось отправить сообщение в мессенджер для сделки {}", leadId);
//            }
            
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
            logger.error("Ошибка при обработке заказа (принят на доставку) для трекера {}: {}", 
                    trackerNumber, e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает заказ, который был доставлен (можно забрать)
     * Находит сделку в Google таблице, обновляет статус в CRM и отправляет сообщение
     * @param trackerNumber номер трекера
     * @param leadId ID сделки (если уже известен, иначе null - будет найден по трекеру)
     */
    public void processDelivered(String trackerNumber, Long leadId) {
        if (leadId == null) {
            leadId = findLeadIdByTracker(trackerNumber);
            if (leadId == null) {
                logger.warn("Не удалось найти ID сделки для трекера {}", trackerNumber);
                return;
            }
        }
        
        try {
            // Обновляем статус сделки на "доставлен"
            boolean statusUpdated = amoCrmService.updateLeadStatus(leadId, AmoLeadStatus.DELIVERED, null);
            if (statusUpdated) {
                logger.info("Статус сделки {} успешно обновлен на '{}' ({})", 
                        leadId, AmoLeadStatus.DELIVERED.getDescription(), AmoLeadStatus.DELIVERED.getStatusId());
            } else {
                logger.warn("Не удалось обновить статус сделки {} на '{}'", 
                        leadId, AmoLeadStatus.DELIVERED.getDescription());
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке заказа (доставлен) для трекера {}: {}", 
                    trackerNumber, e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает заказ, который был вручен клиенту
     * Находит сделку в Google таблице и обновляет статус в CRM
     * @param trackerNumber номер трекера
     * @param leadId ID сделки (если уже известен, иначе null - будет найден по трекеру)
     */
    public void processHandedTo(String trackerNumber, Long leadId) {
        if (leadId == null) {
            leadId = findLeadIdByTracker(trackerNumber);
            if (leadId == null) {
                logger.warn("Не удалось найти ID сделки для трекера {}", trackerNumber);
                return;
            }
        }
        
        try {
            // Обновляем статус сделки на "реализовано"
            boolean statusUpdated = amoCrmService.updateLeadStatus(leadId, AmoLeadStatus.REALIZED, null);
            if (statusUpdated) {
                logger.info("Статус сделки {} успешно обновлен на '{}' ({})", 
                        leadId, AmoLeadStatus.REALIZED.getDescription(), AmoLeadStatus.REALIZED.getStatusId());
            } else {
                logger.warn("Не удалось обновить статус сделки {} на '{}'", 
                        leadId, AmoLeadStatus.REALIZED.getDescription());
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке заказа (вручен клиенту) для трекера {}: {}", 
                    trackerNumber, e.getMessage(), e);
        }
    }

    /**
     * Находит ID сделки по номеру трекера в Google таблице
     * @param trackerNumber номер трекера
     * @return ID сделки или null, если не найдено
     */
    private Long findLeadIdByTracker(String trackerNumber) {
        try {
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsService.readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty()) {
                logger.warn("Таблица '{}' пуста", sheetName);
                return null;
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
                        return null;
                    }
                    
                    // Извлекаем ID сделки из ссылки
                    Long leadId = extractLeadIdFromUrl(dealLink);
                    if (leadId == null) {
                        logger.warn("Не удалось извлечь ID сделки из ссылки: {}", dealLink);
                        return null;
                    }
                    
                    logger.info("Извлечен ID сделки: {} из ссылки: {}", leadId, dealLink);
                    return leadId;
                }
            }
            
            logger.warn("Не найдена строка с трекером {} в таблице '{}'", trackerNumber, sheetName);
            return null;
            
        } catch (Exception e) {
            logger.error("Ошибка при поиске ID сделки по трекеру {}: {}", 
                    trackerNumber, e.getMessage(), e);
            return null;
        }
    }
}
