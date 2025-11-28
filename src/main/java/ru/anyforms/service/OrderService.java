package ru.anyforms.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.ApiResponseDTO;
import ru.anyforms.dto.OrderItemDTO;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.dto.SetTrackerRequestDTO;
import ru.anyforms.dto.SyncOrderRequestDTO;
import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoLead;
import ru.anyforms.model.AmoProduct;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.repository.OrderRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final AmoCrmService amoCrmService;

    /**
     * Синхронизирует заказ из AmoCRM в БД
     * Если заказ уже существует, обновляет его данные
     */
    @Transactional
    public Order syncOrderFromAmoCrm(Long leadId) {
        try {
            // Получаем данные сделки
            AmoLead lead = amoCrmService.getLead(leadId);
            if (lead == null) {
                logger.warn("Lead {} not found in AmoCRM", leadId);
                return null;
            }

            // Получаем контакт
            Long contactId = amoCrmService.getContactIdFromLead(leadId);
            if (contactId == null) {
                logger.warn("Contact not found for lead {}", leadId);
                return null;
            }

            AmoContact contact = amoCrmService.getContact(contactId);
            if (contact == null) {
                logger.warn("Contact {} not found in AmoCRM", contactId);
                return null;
            }

            // Получаем товары из сделки
            List<AmoProduct> products = amoCrmService.getLeadProducts(leadId);

            // Ищем существующий заказ или создаем новый
            Order order = orderRepository.findByLeadId(leadId)
                    .orElse(new Order());

            // Обновляем данные заказа
            order.setLeadId(leadId);
            order.setContactId(contactId);
            order.setContactName(contact.getCustomFieldValue(ru.anyforms.model.AmoCrmFieldId.FIO.getId()));
            order.setContactPhone(contact.getPhone() != null && !contact.getPhone().isEmpty()
                    ? contact.getPhone().get(0).getValue()
                    : null);

            // Получаем трекер из кастомного поля
            String tracker = lead.getCustomFieldValue(ru.anyforms.model.AmoCrmFieldId.TRACKER.getId());
            order.setTracker(tracker);

            // Получаем статус доставки из кастомного поля
            String deliveryStatus = lead.getCustomFieldValue(ru.anyforms.model.AmoCrmFieldId.DELIVERY_STATUS.getId());
            order.setDeliveryStatus(deliveryStatus);

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
            logger.info("Order synced from AmoCRM: leadId={}, items={}", leadId, products.size());
            return savedOrder;
        } catch (Exception e) {
            logger.error("Error syncing order from AmoCRM for lead {}: {}", leadId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Получает все заказы без трекера
     */
    public List<Order> getOrdersWithoutTracker() {
        return orderRepository.findOrdersWithoutTracker();
    }

    /**
     * Получает все заказы без трекера в виде DTO
     */
    public List<OrderSummaryDTO> getOrdersWithoutTrackerDTOs() {
        List<Order> orders = orderRepository.findOrdersWithoutTracker();
        return orders.stream()
                .map(this::convertToOrderSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Конвертирует Order в OrderSummaryDTO
     */
    private OrderSummaryDTO convertToOrderSummaryDTO(Order order) {
        OrderSummaryDTO dto = new OrderSummaryDTO();
        dto.setLeadId(order.getLeadId());
        dto.setContactId(order.getContactId());
        dto.setContactName(order.getContactName());
        dto.setContactPhone(order.getContactPhone());
        dto.setItems(order.getItems().stream()
                .map(this::convertToOrderItemDTO)
                .collect(Collectors.toList()));
        return dto;
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
     * Получает заказ по ID сделки
     */
    public Optional<Order> getOrderByLeadId(Long leadId) {
        return orderRepository.findByLeadId(leadId);
    }

    /**
     * Устанавливает трекер для заказа и обновляет в AmoCRM
     */
    @Transactional
    public boolean setTrackerForOrder(Long leadId, String tracker) {
        try {
            Optional<Order> orderOpt = orderRepository.findByLeadId(leadId);
            if (orderOpt.isEmpty()) {
                logger.warn("Order not found for leadId: {}", leadId);
                return false;
            }

            Order order = orderOpt.get();
            order.setTracker(tracker);
            orderRepository.save(order);

            // Обновляем трекер в AmoCRM
            boolean updated = amoCrmService.updateLeadCustomField(
                    leadId,
                    ru.anyforms.model.AmoCrmFieldId.TRACKER.getId(),
                    tracker
            );

            if (updated) {
                logger.info("Tracker set for order: leadId={}, tracker={}", leadId, tracker);
            } else {
                logger.warn("Failed to update tracker in AmoCRM for leadId: {}", leadId);
            }

            return updated;
        } catch (Exception e) {
            logger.error("Error setting tracker for order leadId {}: {}", leadId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Устанавливает трекер для заказа через DTO
     * Валидирует запрос и возвращает результат в виде DTO
     */
    public ApiResponseDTO setTracker(SetTrackerRequestDTO request) {
        if (request.getLeadId() == null) {
            return new ApiResponseDTO(null, "LeadId is required", null, null, null);
        }

        String tracker = request.getTracker();
        if (tracker == null || tracker.trim().isEmpty()) {
            return new ApiResponseDTO(null, "Tracker is required", null, null, null);
        }

        try {
            boolean success = setTrackerForOrder(request.getLeadId(), tracker.trim());
            if (!success) {
                return new ApiResponseDTO(false, "Failed to set tracker", null, null, null);
            }
            return new ApiResponseDTO(true, null, request.getLeadId(), tracker, null);
        } catch (Exception e) {
            logger.error("Error setting tracker: {}", e.getMessage(), e);
            return new ApiResponseDTO(false, e.getMessage(), null, null, null);
        }
    }

    /**
     * Синхронизирует заказ из AmoCRM через DTO
     * Валидирует запрос и возвращает результат в виде DTO
     */
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
            logger.error("Error syncing order: {}", e.getMessage(), e);
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
                logger.warn("Order not found for leadId: {}", leadId);
                return false;
            }

            Order order = orderOpt.get();
            order.setDeliveryStatus(status);
            orderRepository.save(order);

            // Обновляем статус в AmoCRM
            boolean updated = amoCrmService.updateLeadCustomField(
                    leadId,
                    ru.anyforms.model.AmoCrmFieldId.DELIVERY_STATUS.getId(),
                    status
            );

            if (updated) {
                logger.info("Delivery status updated for order: leadId={}, status={}", leadId, status);
            }

            return updated;
        } catch (Exception e) {
            logger.error("Error updating delivery status for order leadId {}: {}", leadId, e.getMessage(), e);
            return false;
        }
    }
}


