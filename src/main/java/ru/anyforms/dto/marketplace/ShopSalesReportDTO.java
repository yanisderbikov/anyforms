package ru.anyforms.dto.marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Отчёт по продажам магазина за период: сколько молдов куплено и на какую сумму. */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ShopSalesReportDTO {
    private String shopSlug;
    private String shopName;
    private LocalDate from;
    private LocalDate to;
    /** Оплаченных заказов за период. */
    private long ordersCount;
    /** Куплено молдов (сумма количеств по всем позициям). */
    private long itemsCount;
    /** Общая сумма продаж в копейках (фактически оплаченные деньги, со скидками). */
    private long totalKopecks;
    private List<ProductSalesDTO> products;

    /** Разбивка по товарам. */
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class ProductSalesDTO {
        private String productName;
        private long quantity;
        /** Сумма по товару в копейках; null — цены позиций стёр синк АМО. */
        private Long totalKopecks;
    }
}
