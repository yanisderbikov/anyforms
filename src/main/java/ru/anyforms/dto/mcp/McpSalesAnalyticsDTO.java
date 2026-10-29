package ru.anyforms.dto.mcp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Продажи за период: итог по выбранным магазинам и разбивка по каждому")
public record McpSalesAnalyticsDTO(
        @Schema(description = "Начало периода, включительно") LocalDate from,
        @Schema(description = "Конец периода, включительно") LocalDate to,
        @Schema(description = "Оплаченных заказов") long ordersCount,
        @Schema(description = "Куплено молдов, штук") long moldsCount,
        @Schema(description = "Средний чек, ₽") BigDecimal averageCheckRub,
        @Schema(description = "Общая сумма оплат, ₽") BigDecimal totalRub,
        List<Shop> shops) {

    @Schema(description = "Продажи одного магазина за период")
    public record Shop(
            String shopSlug,
            String shopName,
            long ordersCount,
            long moldsCount,
            BigDecimal averageCheckRub,
            BigDecimal totalRub,
            List<Product> products) {
    }

    @Schema(description = "Продажи товара за период")
    public record Product(
            String productName,
            long quantity,
            @Schema(description = "Сумма по товару, ₽; null — цены позиций стёр синк amoCRM") BigDecimal totalRub) {
    }
}
