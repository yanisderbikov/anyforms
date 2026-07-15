package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.model.DeliveryMethod;

import java.util.List;

/** Публичная карточка заказа маркетплейса — без персональных данных (ФИО/телефон/почта). */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Заказ маркетплейса по публичному номеру (без персональных данных)")
public class PublicOrderDTO {

    @Schema(description = "Публичный номер заказа", example = "A1B2C3")
    private String orderNumber;

    @Schema(description = "Статус оплаты", example = "PAID")
    private String paymentStatus;

    @Schema(description = "Способ получения заказа", example = "CDEK")
    private DeliveryMethod deliveryMethod;

    @Schema(description = "Город ПВЗ СДЭК", example = "Москва")
    private String pvzCity;

    @Schema(description = "Адрес ПВЗ СДЭК", example = "ул. Ленина, 1")
    private String pvzStreet;

    @Schema(description = "Итого в рублях", example = "1190.00")
    private String totalRub;

    @Schema(description = "Позиции заказа")
    private List<Item> items;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "Позиция заказа")
    public static class Item {

        @Schema(description = "Название товара", example = "Корпус для Raspberry Pi 5")
        private String name;

        @Schema(description = "Количество", example = "2")
        private Integer quantity;

        @Schema(description = "Цена за штуку в рублях", example = "890.00")
        private String priceRub;

        @Schema(description = "Сумма по позиции в рублях", example = "1780.00")
        private String amountRub;
    }
}
