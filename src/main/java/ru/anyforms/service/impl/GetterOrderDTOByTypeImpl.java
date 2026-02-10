package ru.anyforms.service.impl;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.model.CdekOrderStatus;
import ru.anyforms.model.Order;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.GetterOrderDTOByType;
import ru.anyforms.util.converter.ConverterOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Log4j2
@Component
public class GetterOrderDTOByTypeImpl implements GetterOrderDTOByType {

    private final OrderRepository orderRepository;
    private final ConverterOrder converterOrder;

    public List<OrderSummaryDTO> getOrdersWithoutTrackerDTOs() {
        List<Order> orders = orderRepository.findOrdersWithoutTracker();
        return convertAndSort(orders);
    }

    @Override
    public List<OrderSummaryDTO> getDeliveringOrders() {
        List<Order> orders = orderRepository.findOrdersFilledTrackerExceptDeliveryStatus(
                CdekOrderStatus.DELIVERED.getCode(),
                CdekOrderStatus.CREATED.getCode()
        );
        return convertAndSort(orders);
    }

    @Override
    public List<OrderSummaryDTO> getCreatedOrders() {
        List<Order> orders = orderRepository.getEmptyOrCreatedDeliveryAndNonEmptyTracker();
        return convertAndSort(orders);
    }

    @NotNull
    private List<OrderSummaryDTO> convertAndSort(List<Order> orders) {
        return orders.stream()
                .sorted((o1, o2) -> {
                    LocalDateTime date1 = o1.getPurchaseDate() != null ? o1.getPurchaseDate() : LocalDateTime.MIN;
                    LocalDateTime date2 = o2.getPurchaseDate() != null ? o2.getPurchaseDate() : LocalDateTime.MIN;
                    return date1.compareTo(date2);
                })
                .map(converterOrder::convert)
                .collect(Collectors.toList());
    }
}
