package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.ApiResponseDTO;
import ru.anyforms.dto.OrderItemDTO;
import ru.anyforms.dto.SetTrackerAndCommentRequestDTO;
import ru.anyforms.dto.SyncOrderRequestDTO;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.integration.CdekTrackingGateway;
import ru.anyforms.integration.GoogleSheetsGateway;
import ru.anyforms.model.*;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.OrderService;
import ru.anyforms.util.sheets.GoogleSheetsColumnIndex;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Service
@RequiredArgsConstructor
class OrderServiceImpl implements OrderService  {

    // Паттерн для извлечения ID сделки из URL
    private static final Pattern LEAD_ID_PATTERN = Pattern.compile("leads/detail/(\\d+)");
    
    private final OrderRepository orderRepository;
    private final AmoCrmGateway amoCrmGateway;
    private final GoogleSheetsGateway googleSheetsGateway;
    private final CdekTrackingGateway cdekTrackingGateway;
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    /**
     * Синхронизирует заказ из AmoCRM в БД
     * Если заказ уже существует, обновляет его данные
     */
    @Transactional
    public Order syncOrderFromAmoCrm(Long leadId) {
        try {
            // Получаем данные сделки
            AmoLead lead = amoCrmGateway.getLead(leadId);
            if (lead == null) {
                log.warn("Lead {} not found in AmoCRM", leadId);
                return null;
            }

            // Получаем контакт
            Long contactId = amoCrmGateway.getContactIdFromLead(leadId);
            if (contactId == null) {
                log.warn("Contact not found for lead {}", leadId);
                return null;
            }

            AmoContact contact = amoCrmGateway.getContact(contactId);
            if (contact == null) {
                log.warn("Contact {} not found in AmoCRM", contactId);
                return null;
            }

            // Получаем товары из сделки
            List<AmoProduct> products = amoCrmGateway.getLeadProducts(leadId);
            if (products == null || products.isEmpty()) {
                log.warn("no products for lead {}", leadId);
                return null;
            }

            // Ищем существующий заказ или создаем новый
            Order order = orderRepository.findByLeadId(leadId)
                    .orElse(new Order());

            // Обновляем данные заказа
            order.setLeadId(leadId);
            order.setContactId(contactId);
            order.setContactName(contact.getCustomFieldValue(AmoCrmFieldId.FIO.getId()));
            order.setContactPhone(contact.getPhone() != null && !contact.getPhone().isEmpty()
                    ? contact.getPhone().get(0).getValue()
                    : null);

            // Получаем трекер из кастомного поля
            String tracker = lead.getCustomFieldValue(AmoCrmFieldId.TRACKER.getId());
            order.setTracker(tracker);

            String deliveryStatus;
            if (tracker != null && !tracker.isEmpty()) {
                if (lead.getPipelineId().equals(AmoLeadStatus.REALIZED)) {
                    deliveryStatus = CdekOrderStatus.DELIVERED.getCode();
                } else {
                    deliveryStatus = cdekTrackingGateway.getOrderStatus(tracker);
                }
            } else {
                deliveryStatus = lead.getCustomFieldValue(AmoCrmFieldId.DELIVERY_STATUS.getId());
            }
            order.setDeliveryStatus(deliveryStatus);

            // Получаем ПВЗ СДЭК из контакта
            String pvzSdek = contact.getCustomFieldValue(AmoCrmFieldId.CONTACT_PVZ.getId());
            order.setPvzSdek(pvzSdek);

            // Получаем дату покупки из сделки
            // todo 3 исправить дату
            String datePaymentValue = lead.getCustomFieldValue(AmoCrmFieldId.DATE_PAYMENT.getId());
            if (datePaymentValue != null && !datePaymentValue.trim().isEmpty()) {
                LocalDateTime purchaseDate = parseDateFromAmoCrm(datePaymentValue);
                order.setPurchaseDate(purchaseDate);
            } else {
                order.setPurchaseDate(null);
            }

            // Обновляем товары
            order.getItems().clear();
            for (AmoProduct product : products) {
                OrderItem item = new OrderItem();
                item.setProductName(product.getName());
                item.setQuantity(product.getQuantity() != null ? product.getQuantity() : 1);
                item.setProductId(product.getId());
                item.setCatalogId(product.getCatalogId());
                order.addItem(item);
            }

            Order savedOrder = orderRepository.save(order);
            log.info("Order synced from AmoCRM: leadId={}, items={}", leadId, products.size());
            return savedOrder;
        } catch (Exception e) {
            log.error("Error syncing order from AmoCRM for lead {}: {}", leadId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Парсит дату из значения поля AmoCRM
     * Поддерживает timestamp (Unix timestamp в секундах или миллисекундах)
     */
    private LocalDateTime parseDateFromAmoCrm(String dateValue) {
        if (dateValue == null || dateValue.trim().isEmpty()) {
            return null;
        }
        try {
            // Пытаемся распарсить как timestamp (Unix timestamp в секундах или миллисекундах)
            long timestamp;
            if (dateValue.length() > 10) {
                timestamp = Long.parseLong(dateValue) / 1000; // Предполагаем миллисекунды
            } else {
                timestamp = Long.parseLong(dateValue);
            }
            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse date from AmoCRM value '{}': {}", dateValue, e.getMessage());
            return null;
        }
    }

    /**
     * Конвертирует OrderItem в OrderItemDTO
     */
    private OrderItemDTO convertToOrderItemDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setProductName(item.getProductName());
        dto.setQuantity(item.getQuantity());
        dto.setProductId(item.getProductId());
        return dto;
    }

    /**
     * Устанавливает трекер для заказа и обновляет в AmoCRM и Google Sheets
     */
    @Transactional
    public boolean setTrackerAndCommentForOrder(Long leadId, String tracker, String comment) {
        try {
            Optional<Order> orderOpt = orderRepository.findByLeadId(leadId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for leadId: {}", leadId);
                return false;
            }

            Order order = orderOpt.get();
            if (order.getTracker() == null) {
                order.setTracker(tracker);
            } else {
                log.warn("cannot update tracker if it already set");
            }
            order.setComment(comment);
            orderRepository.save(order);

            // Обновляем трекер в AmoCRM
            boolean updated = amoCrmGateway.updateLeadCustomField(
                    leadId,
                    ru.anyforms.model.AmoCrmFieldId.TRACKER.getId(),
                    tracker
            );

            if (updated) {
                log.info("Tracker set for order: leadId={}, tracker={}", leadId, tracker);
            } else {
                log.warn("Failed to update tracker in AmoCRM for leadId: {}", leadId);
            }

            // Обновляем трекер в таблице "Лошадки"
            writeTrackerToGoogleSheet(leadId, tracker);

            return updated;
        } catch (Exception e) {
            log.error("Error setting tracker for order leadId {}: {}", leadId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Устанавливает трекер для заказа через DTO
     * Валидирует запрос и возвращает результат в виде DTO
     */
    public ApiResponseDTO setTrackerAndComment(SetTrackerAndCommentRequestDTO request) {
        if (request.getLeadId() == null) {
            return new ApiResponseDTO(null, "LeadId is required", null, null, null);
        }

        String tracker = request.getTracker();

        try {
            boolean success = setTrackerAndCommentForOrder(request.getLeadId(), tracker.trim(), request.getComment());
            if (!success) {
                return new ApiResponseDTO(false, "Failed to set tracker", null, null, null);
            }
            return new ApiResponseDTO(true, null, request.getLeadId(), tracker, null);
        } catch (Exception e) {
            log.error("Error setting tracker: {}", e.getMessage(), e);
            return new ApiResponseDTO(false, e.getMessage(), null, null, null);
        }
    }

    /**
     * Синхронизирует заказ из AmoCRM через DTO
     * Валидирует запрос и возвращает результат в виде DTO
     */
    @Transactional
    public ApiResponseDTO syncOrder(SyncOrderRequestDTO request) {
        if (request.getLeadId() == null) {
            return new ApiResponseDTO(null, "LeadId is required", null, null, null);
        }

        try {
            Order order = syncOrderFromAmoCrm(request.getLeadId());
            if (order == null) {
                return new ApiResponseDTO(false, "Order not found or failed to sync", null, null, null);
            }
            return new ApiResponseDTO(true, null, request.getLeadId(), null, order.getItems().size());
        } catch (Exception e) {
            log.error("Error syncing order: {}", e.getMessage(), e);
            return new ApiResponseDTO(false, e.getMessage(), null, null, null);
        }
    }

    /**
     * Обновляет статус доставки в заказе и в AmoCRM
     */
    @Transactional
    public boolean updateDeliveryStatus(Long leadId, String status) {
        try {
            Optional<Order> orderOpt = orderRepository.findByLeadId(leadId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for leadId: {}", leadId);
                return false;
            }

            Order order = orderOpt.get();
            order.setDeliveryStatus(status);
            orderRepository.save(order);

            // Обновляем статус в AmoCRM
            boolean updated = amoCrmGateway.updateLeadCustomField(
                    leadId,
                    ru.anyforms.model.AmoCrmFieldId.DELIVERY_STATUS.getId(),
                    status
            );

            if (updated) {
                log.info("Delivery status updated for order: leadId={}, status={}", leadId, status);
            }

            // Записываем трекер в Google таблицу
            writeTrackerToGoogleSheet(leadId, order.getTracker());

            return updated;
        } catch (Exception e) {
            log.error("Error updating delivery status for order leadId {}: {}", leadId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Записывает трекер в Google таблицу в столбец с трекером
     * Находит строку по leadId в столбце E (ссылка на сделку) и записывает трекер в столбец I
     * @param leadId ID сделки
     * @param tracker номер трекера для записи
     */
    private void writeTrackerToGoogleSheet(Long leadId, String tracker) {
        if (leadId == null) {
            log.warn("Cannot write tracker to Google Sheet: leadId is null");
            return;
        }

        if (tracker == null || tracker.trim().isEmpty()) {
            log.debug("Tracker is empty for leadId {}, skipping Google Sheet update", leadId);
            return;
        }

        try {
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsGateway.readAllRows(sheetName);

            if (allRows == null || allRows.isEmpty()) {
                log.warn("Таблица '{}' пуста, не удалось записать трекер для leadId {}", sheetName, leadId);
                return;
            }

            // Ищем строку с нужным leadId (начиная со второй строки, пропуская заголовок)
            for (int i = 1; i < allRows.size(); i++) {
                List<Object> row = allRows.get(i);
                String dealLink = googleSheetsGateway.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_E_INDEX);

                if (dealLink == null || dealLink.trim().isEmpty()) {
                    continue;
                }

                // Извлекаем ID сделки из ссылки
                Long rowLeadId = extractLeadIdFromUrl(dealLink);
                if (rowLeadId == null || !rowLeadId.equals(leadId)) {
                    continue;
                }

                // Нашли строку с нужным leadId, записываем трекер в столбец I
                int rowNumber = i + 1; // Номер строки в таблице (1-based)
                googleSheetsGateway.writeCell(sheetName, rowNumber, GoogleSheetsColumnIndex.COLUMN_I_INDEX, tracker.trim());
                log.info("Tracker written to Google Sheet: leadId={}, tracker={}, row={}", leadId, tracker, rowNumber);
                return;
            }

            log.warn("Строка с leadId {} не найдена в таблице '{}'", leadId, sheetName);
        } catch (Exception e) {
            log.error("Ошибка при записи трекера в Google таблицу для leadId {}: {}",
                    leadId, e.getMessage(), e);
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
                log.warn("Не удалось преобразовать ID сделки в число: {}", matcher.group(1));
                return null;
            }
        }

        return null;
    }
}


