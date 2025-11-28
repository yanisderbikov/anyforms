package ru.anyforms.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderSummaryDTO {
    private Long leadId;
    private Long contactId;
    private String contactName;
    private String contactPhone;
    private List<OrderItemDTO> items;
}

