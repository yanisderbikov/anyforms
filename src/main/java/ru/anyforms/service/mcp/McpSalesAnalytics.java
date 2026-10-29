package ru.anyforms.service.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.marketplace.ShopSalesReportDTO;
import ru.anyforms.dto.mcp.McpSalesAnalyticsDTO;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterShop;
import ru.anyforms.service.product.ShopSalesReportService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class McpSalesAnalytics {

    private final ShopSalesReportService reportService;
    private final GetterShop getterShop;

    public McpSalesAnalyticsDTO sales(LocalDate from, LocalDate to, String shopSlug) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Дата начала позже даты конца");
        }
        List<String> slugs = shopSlug != null
                ? List.of(shopSlug)
                : getterShop.getAllShops().stream().map(Shop::getSlug).toList();
        List<McpSalesAnalyticsDTO.Shop> shops = slugs.stream()
                .map(slug -> toShop(reportService.getReport(slug, from, to)))
                .toList();
        long ordersCount = shops.stream().mapToLong(McpSalesAnalyticsDTO.Shop::ordersCount).sum();
        long moldsCount = shops.stream().mapToLong(McpSalesAnalyticsDTO.Shop::moldsCount).sum();
        BigDecimal total = shops.stream().map(McpSalesAnalyticsDTO.Shop::totalRub).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new McpSalesAnalyticsDTO(from, to, ordersCount, moldsCount, average(total, ordersCount), total, shops);
    }

    private McpSalesAnalyticsDTO.Shop toShop(ShopSalesReportDTO report) {
        BigDecimal total = rub(report.getTotalKopecks());
        List<McpSalesAnalyticsDTO.Product> products = report.getProducts().stream()
                .map(p -> new McpSalesAnalyticsDTO.Product(p.getProductName(), p.getQuantity(),
                        p.getTotalKopecks() == null ? null : rub(p.getTotalKopecks())))
                .toList();
        return new McpSalesAnalyticsDTO.Shop(report.getShopSlug(), report.getShopName(), report.getOrdersCount(),
                report.getItemsCount(), average(total, report.getOrdersCount()), total, products);
    }

    private static BigDecimal rub(long kopecks) {
        return BigDecimal.valueOf(kopecks, 2);
    }

    private static BigDecimal average(BigDecimal total, long ordersCount) {
        return ordersCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(ordersCount), 2, RoundingMode.HALF_UP);
    }
}
