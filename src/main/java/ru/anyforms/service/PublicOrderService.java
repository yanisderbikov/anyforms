package ru.anyforms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.PublicOrderDTO;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.OrderSource;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.util.MoneyUtil;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Публичная карточка заказа маркетплейса по 6-символьному номеру — для страницы
 * после оплаты. Отдаёт только состав, суммы и ПВЗ; персональные данные (ФИО,
 * телефон, почта) наружу не выходят — номер заказа могут подсмотреть в URL.
 */
@Service
@RequiredArgsConstructor
public class PublicOrderService {

    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile("[A-Z0-9]{6}");

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PublicOrderDTO getByPublicId(String rawPublicId) {
        String publicId = rawPublicId == null ? "" : rawPublicId.trim().toUpperCase();
        if (!PUBLIC_ID_PATTERN.matcher(publicId).matches()) {
            throw notFound();
        }
        Order order = orderRepository.findByPublicId(publicId)
                .filter(o -> o.getSource() == OrderSource.MARKETPLACE)
                .orElseThrow(PublicOrderService::notFound);

        List<PublicOrderDTO.Item> items = order.getItems().stream()
                .map(PublicOrderService::toItem)
                .collect(Collectors.toList());

        return PublicOrderDTO.builder()
                .orderNumber(order.getPublicId())
                .paymentStatus(order.getPaymentStatus().name())
                .deliveryMethod(order.getDeliveryMethod())
                .pvzCity(order.getPvzSdekCity())
                .pvzStreet(order.getPvzSdekStreet())
                .totalRub(totalRub(order.getItems()))
                .items(items)
                .build();
    }

    private static PublicOrderDTO.Item toItem(OrderItem item) {
        int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
        Long priceKopecks = item.getPriceKopecks();
        return PublicOrderDTO.Item.builder()
                .name(item.getProductName())
                .quantity(quantity)
                .priceRub(priceKopecks != null ? MoneyUtil.kopecksToString(priceKopecks) : null)
                .amountRub(priceKopecks != null ? MoneyUtil.kopecksToString(priceKopecks * quantity) : null)
                .build();
    }

    /** Синк из АМО может пересоздать позиции без цены — тогда итог не считаем. */
    private static String totalRub(List<OrderItem> items) {
        long total = 0;
        for (OrderItem item : items) {
            if (item.getPriceKopecks() == null) {
                return null;
            }
            total += item.getPriceKopecks() * (item.getQuantity() == null ? 1 : item.getQuantity());
        }
        return MoneyUtil.kopecksToString(total);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден");
    }
}
