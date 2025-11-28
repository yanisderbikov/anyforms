package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Сводка по заказу без трекера")
public class OrderSummaryDTO {
    @Schema(description = "ID сделки в AmoCRM", example = "12345")
    private Long leadId;
    
    @Schema(description = "ID контакта в AmoCRM", example = "67890")
    private Long contactId;
    
    @Schema(description = "Имя контакта", example = "Иван Иванов")
    private String contactName;
    
    @Schema(description = "Телефон контакта", example = "+79991234567")
    private String contactPhone;
    
    @Schema(description = "ПВЗ СДЭК", example = "Москва, ул. Ленина, 1")
    private String pvzSdek;
    
    @Schema(description = "Дата покупки", example = "2024-01-15T10:30:00")
    private LocalDateTime purchaseDate;
    
    @Schema(description = "Список товаров в заказе")
    private List<OrderItemDTO> items;
}

