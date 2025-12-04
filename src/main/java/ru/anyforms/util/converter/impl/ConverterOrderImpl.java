package ru.anyforms.util.converter.impl;

import org.springframework.stereotype.Component;
import ru.anyforms.dto.OrderItemDTO;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.model.CdekOrderStatus;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.util.converter.ConverterOrder;

import java.util.stream.Collectors;

@Component
class ConverterOrderImpl implements ConverterOrder {
    @Override
    public OrderSummaryDTO convert(Order order) {

        var orderStatus = CdekOrderStatus.fromCode(order.getDeliveryStatus());
        var dtoStatus = orderStatus == CdekOrderStatus.UNKNOWN ? "" : orderStatus.getDescription();

        OrderSummaryDTO dto = new OrderSummaryDTO();
        dto.setLeadId(order.getLeadId());
        dto.setContactId(order.getContactId());
        dto.setContactName(order.getContactName());
        dto.setContactPhone(order.getContactPhone());
        dto.setPvzSdek(order.getPvzSdek());
        dto.setPurchaseDate(order.getPurchaseDate());
        dto.setComment(order.getComment());
        dto.setTracker(order.getTracker());
        dto.setDeliveryStatus(dtoStatus);
        dto.setItems(order.getItems().stream()
                .map(this::convertToOrderItemDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private OrderItemDTO convertToOrderItemDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setProductName(item.getProductName());
        dto.setQuantity(item.getQuantity());
        dto.setProductId(item.getProductId());
        return dto;
    }
}
