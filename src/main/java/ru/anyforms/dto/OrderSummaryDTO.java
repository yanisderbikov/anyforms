package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import ru.anyforms.model.DeliveryMethod;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Сводка по заказу без трекера")
public class OrderSummaryDTO {
    @Schema(description = "Наш ID заказа", example = "1")
    private Long id;

    @Schema(description = "ID сделки в AmoCRM", example = "12345")
    private Long leadId;
    
    @Schema(description = "ID контакта в AmoCRM", example = "67890")
    private Long contactId;
    
    @Schema(description = "Имя контакта", example = "Иван Иванов")
    private String contactName;
    
    @Schema(description = "Телефон контакта", example = "+79991234567")
    private String contactPhone;
    
    @Schema(description = "ПВЗ СДЭК улица", example = "ул. Ленина, 1")
    private String pvzSdekStreet;

    @Schema(description = "ПВЗ СДЭК город", example = "Москва")
    private String pvzSdekCity;

    @Schema(description = "Дата покупки", example = "2024-01-15T10:30:00")
    private LocalDateTime purchaseDate;

    @Schema(description = "Комментарий", example = "Инфа о доставки например")
    private String comment;

    @Schema(description = "Статус доставки", example = "Статус доставки")
    private String deliveryStatus;

    @Schema(description = "Трекер доставки", example = "121212")
    private String tracker;

    @Schema(description = "Способ получения заказа", example = "PICKUP")
    private DeliveryMethod deliveryMethod;
    
    @Schema(description = "Список товаров в заказе")
    private List<OrderItemDTO> items;

    @Schema(description = "Кол-во кастомных позиций под-заказа", example = "0")
    private Long customItemsCount;
}

