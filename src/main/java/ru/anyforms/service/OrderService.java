package ru.anyforms.service;

import ru.anyforms.dto.ApiResponseDTO;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.dto.SetTrackerRequestDTO;
import ru.anyforms.dto.SyncOrderRequestDTO;
import ru.anyforms.model.Order;

import java.util.List;

public interface OrderService {
    Order syncOrderFromAmoCrm(Long leadId);
    boolean setTrackerForOrder(Long leadId, String tracker);
    List<OrderSummaryDTO> getOrdersWithoutTrackerDTOs();
    boolean updateDeliveryStatus(Long leadId, String status);
    ApiResponseDTO setTracker(SetTrackerRequestDTO request);
    ApiResponseDTO syncOrder(SyncOrderRequestDTO request);
}
