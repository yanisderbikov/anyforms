package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Заказ (как в рознице — полный набор полей OrderSummaryDTO) + его позиции,
 * готовые к отправке. Для вкладки «к отправке».
 */
@Data
@Schema(description = "Заказ с позициями, готовыми к отправке")
public class ShipGroupDTO {

    /** Полный заказ (ФИО, телефон, ПВЗ, комментарий, дата и т.д.). */
    private OrderSummaryDTO order;

    /** Кастомные позиции заказа в статусе READY_TO_SHIP. */
    private List<CustomProductItemDTO> items;
}
