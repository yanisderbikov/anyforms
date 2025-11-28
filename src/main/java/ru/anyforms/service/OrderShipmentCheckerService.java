package ru.anyforms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.SetTrackerRequestDTO;
import ru.anyforms.model.CdekOrderStatus;

import java.util.List;

@Service
public class OrderShipmentCheckerService {
    private static final Logger logger = LoggerFactory.getLogger(OrderShipmentCheckerService.class);
    
    private final GoogleSheetsService googleSheetsService;
    private final CdekTrackingService cdekTrackingService;
    private final DeliveryProcessor deliveryProcessor;
    private final OrderService orderService;
    
    // Индексы колонок (0-based: A=0, B=1, ..., I=8, J=9, E=4)
    private static final int COLUMN_I_INDEX = 8;  // Колонка I (трекер)
    private static final int COLUMN_J_INDEX = 9;  // Колонка J (статус)
    private static final int COLUMN_E_INDEX = 4;  // Колонка E (ссылка на сделку)
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    public OrderShipmentCheckerService(GoogleSheetsService googleSheetsService,
                                       CdekTrackingService cdekTrackingService,
                                       DeliveryProcessor deliveryProcessor, OrderService orderService) {
        this.googleSheetsService = googleSheetsService;
        this.cdekTrackingService = cdekTrackingService;
        this.deliveryProcessor = deliveryProcessor;
        this.orderService = orderService;
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
            if (CdekOrderStatus.NOT_FOUND_OR_DELIVERED.getCode().equals(newStatusCode)) {
                logger.info("Заказ {} в строке {} не найден или доставлен, записываем статус в таблицу", 
                        trackingNumber, rowNumber);
                deliveryProcessor.updateStatusInTableAndAmoCrm(rowNumber, newStatusCode);
                Long leadId = extractLeadIdFromRow(row);
                if (leadId != null) {
                    deliveryProcessor.updateAmoCrmStatusIfNeeded(leadId, trackingNumber, newStatusCode);
                }
                return false;
            }
            
            // Если статус DELIVERED, записываем и не обрабатываем дальше
            if (CdekOrderStatus.DELIVERED.getCode().equals(newStatusCode)) {
                logger.info("Заказ {} в строке {} доставлен, записываем статус в таблицу", 
                        trackingNumber, rowNumber);
                deliveryProcessor.updateStatusInTableAndAmoCrm(rowNumber, newStatusCode);
                Long leadId = extractLeadIdFromRow(row);
                if (leadId != null) {
                    deliveryProcessor.updateAmoCrmStatusIfNeeded(leadId, trackingNumber, newStatusCode);
                }
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
                
                // Обновляем статус в таблице и в AmoCRM одновременно
                deliveryProcessor.updateStatusInTableAndAmoCrm(rowNumber, newStatusCode);
                
                // Обновляем статус в AmoCRM, если нужно
                Long leadId = extractLeadIdFromRow(row);
                if (leadId != null) {
                    deliveryProcessor.updateAmoCrmStatusIfNeeded(leadId, trackingNumber, newStatusCode);
                    orderService.setTracker(new SetTrackerRequestDTO(leadId, trackingNumber));
                }
                
                // Если заказ отправлен (более чем RECEIVED_AT_SHIPMENT_WAREHOUSE), обрабатываем отправку
                if (isShipped(orderStatus)) {
                    logger.info("Заказ {} в строке {} отправлен (статус: {}), обрабатываем...", 
                            trackingNumber, rowNumber, newStatusCode);
                    
                    // Обрабатываем отправленный заказ (добавляем трекер, отправляем сообщение, обновляем статус на SENT)
                    if (leadId != null) {
                        deliveryProcessor.processAcceptedForDelivery(trackingNumber, leadId);
                    } else {
                        deliveryProcessor.processAcceptedForDelivery(trackingNumber, null);
                    }
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
               status == CdekOrderStatus.ACCEPTED_AT_PICK_UP_POINT ||
               status == CdekOrderStatus.DELIVERED_TO_PICKUP_POINT ||
               status == CdekOrderStatus.ISSUED_FOR_DELIVERY ||
               status == CdekOrderStatus.DELIVERED ||
               status == CdekOrderStatus.HANDED_TO;
    }
    
    /**
     * Извлекает ID сделки из строки таблицы
     * @param row строка таблицы
     * @return ID сделки или null, если не удалось извлечь
     */
    private Long extractLeadIdFromRow(List<Object> row) {
        // Получаем ссылку на сделку из столбца E
        String dealLink = googleSheetsService.getCellValue(row, COLUMN_E_INDEX);
        
        if (dealLink == null || dealLink.trim().isEmpty()) {
            return null;
        }
        
        return deliveryProcessor.extractLeadIdFromUrl(dealLink);
    }
    
}

