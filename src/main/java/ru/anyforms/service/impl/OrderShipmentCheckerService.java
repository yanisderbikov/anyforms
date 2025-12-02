package ru.anyforms.service.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.SetTrackerRequestDTO;
import ru.anyforms.model.CdekOrderStatus;
import ru.anyforms.service.DeliveryProcessor;
import ru.anyforms.service.OrderService;
import ru.anyforms.util.sheets.GoogleSheetsColumnIndex;
import ru.anyforms.util.sheets.SheetRowExtractorUtil;

import java.util.List;

@Log4j2
@Service
public class OrderShipmentCheckerService {

    private final ru.anyforms.integration.GoogleSheetsGateway googleSheetsGateway;
    private final ru.anyforms.integration.CdekTrackingGateway cdekTrackingGateway;
    private final DeliveryProcessor deliveryProcessor;
    private final OrderService orderService;
    private final SheetRowExtractorUtil sheetRowExtractorUtil;
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    public OrderShipmentCheckerService(ru.anyforms.integration.GoogleSheetsGateway googleSheetsGateway,
                                       ru.anyforms.integration.CdekTrackingGateway cdekTrackingGateway,
                                       DeliveryProcessor deliveryProcessor, OrderService orderService, SheetRowExtractorUtil sheetRowExtractorUtil) {
        this.googleSheetsGateway = googleSheetsGateway;
        this.cdekTrackingGateway = cdekTrackingGateway;
        this.deliveryProcessor = deliveryProcessor;
        this.orderService = orderService;
        this.sheetRowExtractorUtil = sheetRowExtractorUtil;
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
            log.info("Начало проверки всех заказов на отправку в таблице '{}'", sheetName);
            
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsGateway.readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty()) {
                log.info("В таблице '{}' нет строк", sheetName);
                return;
            }
            
            // Обрабатываем строки начиная со второй (пропускаем заголовок)
            for (int i = 1; i < allRows.size(); i++) {
                List<Object> row = allRows.get(i);
                int rowNumber = i + 1; // Номер строки в таблице (1-based)
                
                // Проверяем условия
                if (shouldProcessRow(row, rowNumber)) {
                    String trackingNumber = googleSheetsGateway.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_I_INDEX);
                    
                    log.info("Проверка заказа в строке {}: трекер {}", rowNumber, trackingNumber);
                    checkAndProcessShippedOrder(row, rowNumber, trackingNumber);
                }
            }
            
            log.info("Проверка завершена. ");
            
        } catch (Exception e) {
            log.error("Ошибка при проверке заказов на отправку: {}", e.getMessage(), e);
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
        
        String columnI = googleSheetsGateway.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_I_INDEX);
        String columnJ = googleSheetsGateway.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_J_INDEX);
        
        // Проверка 1: Колонка I должна содержать валидный трекер
        if (columnI.isEmpty() || !cdekTrackingGateway.isValidTrackingNumber(columnI)) {
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
            String currentStatus = googleSheetsGateway.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_J_INDEX);
            String currentStatusUpper = currentStatus.toUpperCase().trim();
            
            // Получаем новый статус из СДЭК
            String newStatusCode = cdekTrackingGateway.getOrderStatusCode(trackingNumber);
            
            if (newStatusCode == null || newStatusCode.isEmpty()) {
                log.warn("Не удалось получить статус для трекера {} в строке {}", trackingNumber, rowNumber);
                return false;
            }
            
            // Если заказ не найден или доставлен, записываем статус в таблицу и не обрабатываем дальше
            if (CdekOrderStatus.NOT_FOUND_OR_DELIVERED.getCode().equals(newStatusCode)) {
                log.info("Заказ {} в строке {} не найден или доставлен, записываем статус в таблицу",
                        trackingNumber, rowNumber);
                deliveryProcessor.updateStatus(rowNumber, newStatusCode);
                Long leadId = sheetRowExtractorUtil.extractLeadIdFromRow(row);
                if (leadId != null) {
                    deliveryProcessor.updateAmoCrmStatusIfNeeded(leadId, trackingNumber, newStatusCode);
                }
                return false;
            }
            
            // Если статус DELIVERED, записываем и не обрабатываем дальше
            if (CdekOrderStatus.DELIVERED.getCode().equals(newStatusCode)) {
                log.info("Заказ {} в строке {} доставлен, записываем статус в таблицу",
                        trackingNumber, rowNumber);
                deliveryProcessor.updateStatus(rowNumber, newStatusCode);
                Long leadId = sheetRowExtractorUtil.extractLeadIdFromRow(row);
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
                log.info("Статус заказа {} в строке {} изменился: {} -> {}",
                        trackingNumber, rowNumber, currentStatus, newStatusCode);
                
                // Обновляем статус в таблице и в AmoCRM одновременно
                deliveryProcessor.updateStatus(rowNumber, newStatusCode);
                
                // Обновляем статус в AmoCRM, если нужно
                Long leadId = sheetRowExtractorUtil.extractLeadIdFromRow(row);
                if (leadId != null) {
                    deliveryProcessor.updateAmoCrmStatusIfNeeded(leadId, trackingNumber, newStatusCode);
                    orderService.setTracker(new SetTrackerRequestDTO(leadId, trackingNumber));
                }
                
                // Если заказ отправлен (более чем RECEIVED_AT_SHIPMENT_WAREHOUSE), обрабатываем отправку
                if (isShipped(orderStatus)) {
                    log.info("Заказ {} в строке {} отправлен (статус: {}), обрабатываем...",
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
                log.debug("Статус заказа {} в строке {} не изменился: {}",
                        trackingNumber, rowNumber, newStatusCode);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при проверке статуса заказа {} в строке {}: {}",
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
}

