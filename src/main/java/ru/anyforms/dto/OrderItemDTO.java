package ru.anyforms.dto;

import lombok.Data;

@Data
public class OrderItemDTO {
    private String productName;
    private Integer quantity;
    private Long productId;
}

