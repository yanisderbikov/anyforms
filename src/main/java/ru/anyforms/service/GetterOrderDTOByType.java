package ru.anyforms.service;

import ru.anyforms.dto.OrderSummaryDTO;

import java.util.List;

public interface GetterOrderDTOByType {
    List<OrderSummaryDTO> getOrdersWithoutTrackerDTOs();
    List<OrderSummaryDTO> getDeliveringOrders();
    List<OrderSummaryDTO> getCreatedOrders();
}
