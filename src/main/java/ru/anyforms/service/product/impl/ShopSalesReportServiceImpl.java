package ru.anyforms.service.product.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.marketplace.ShopSalesReportDTO;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterShop;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.product.ShopSalesReportService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class ShopSalesReportServiceImpl implements ShopSalesReportService {

    private final OrderRepository orderRepository;
    private final GetterShop getterShop;
    private final GetterTransaction getterTransaction;

    @Override
    @Transactional(readOnly = true)
    public ShopSalesReportDTO getReport(String shopSlug, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата начала позже даты конца");
        }
        Shop shop = getterShop.getBySlug(shopSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден: " + shopSlug));

        List<Order> orders = orderRepository.findPaidShopOrders(
                shop.getSlug(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        long itemsCount = 0;
        long totalKopecks = 0;
        Map<String, ShopSalesReportDTO.ProductSalesDTO> byProduct = new LinkedHashMap<>();

        for (Order order : orders) {
            itemsCount += order.getItems().stream().mapToLong(i -> nvl(i.getQuantity())).sum();
            totalKopecks += orderTotalKopecks(order);

            for (OrderItem item : order.getItems()) {
                var row = byProduct.computeIfAbsent(item.getProductName(), name ->
                        ShopSalesReportDTO.ProductSalesDTO.builder().productName(name).totalKopecks(0L).build());
                row.setQuantity(row.getQuantity() + nvl(item.getQuantity()));
                if (item.getPriceKopecks() == null) {
                    // Синк АМО стёр цену позиции — сумма по товару неизвестна.
                    row.setTotalKopecks(null);
                } else if (row.getTotalKopecks() != null) {
                    row.setTotalKopecks(row.getTotalKopecks() + item.getPriceKopecks() * nvl(item.getQuantity()));
                }
            }
        }

        return ShopSalesReportDTO.builder()
                .shopSlug(shop.getSlug())
                .shopName(shop.getName())
                .from(from)
                .to(to)
                .ordersCount(orders.size())
                .itemsCount(itemsCount)
                .totalKopecks(totalKopecks)
                .products(new ArrayList<>(byProduct.values()))
                .build();
    }

    /**
     * Сумма заказа: приоритетно фактический платёж Юкассы (учитывает промокоды и
     * переживает синк АМО, который пересоздаёт позиции без цен), иначе — сумма позиций.
     */
    private long orderTotalKopecks(Order order) {
        long paid = getterTransaction.getByOrderId(order.getId()).stream()
                .filter(t -> t.getStatus() == PaymentTransactionStatus.SUCCEEDED)
                .mapToLong(t -> nvl(t.getAmount()))
                .sum();
        if (paid > 0) {
            return paid;
        }
        return order.getItems().stream()
                .filter(i -> i.getPriceKopecks() != null)
                .mapToLong(i -> i.getPriceKopecks() * nvl(i.getQuantity()))
                .sum();
    }

    private static long nvl(Number value) {
        return value == null ? 0 : value.longValue();
    }
}
