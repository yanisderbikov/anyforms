package ru.anyforms.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Тело таски на письмо-чек заказа маркетплейса: снапшот заказа на момент оплаты
 * (позиции, итог, ПВЗ, получатель), чтобы раннер рендерил письмо без обращения к БД.
 * У курса/гайда свой пейлоад — {@link EmailTaskPayload}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MarketplaceOrderEmailPayload {
    private String to;
    private String orderPublicId;
    private String customerName;
    private String pvzCity;
    private String pvzStreet;
    private String totalRub;
    private List<Item> items;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Item {
        private String name;
        private Integer quantity;
        private String priceRub;
    }
}
