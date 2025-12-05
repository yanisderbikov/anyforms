package ru.anyforms.service;

import ru.anyforms.dto.ApiResponseDTO;
import ru.anyforms.dto.SetTrackerAndCommentRequestDTO;
import ru.anyforms.dto.SyncOrderRequestDTO;
import ru.anyforms.model.Order;

public interface OrderService {
    Order syncOrderFromAmoCrm(Long leadId);
    boolean setTrackerAndCommentForOrder(Long leadId, String tracker, String comment);
    boolean updateDeliveryStatus(Long leadId, String status);
    ApiResponseDTO setTrackerAndComment(SetTrackerAndCommentRequestDTO request);
    ApiResponseDTO syncOrder(SyncOrderRequestDTO request);
    default ApiResponseDTO syncOrder(Long leadId) {
        return syncOrder(new SyncOrderRequestDTO(leadId));
    }
}
