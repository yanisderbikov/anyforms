package ru.anyforms.service.telegram.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.telegram.TelegramDigestDTO;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.TelegramNotification;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.TelegramNotificationRepository;
import ru.anyforms.service.telegram.TelegramDigestService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
class TelegramDigestServiceImpl implements TelegramDigestService {

    private static final String ORDERS_WITHOUT_TRACKER_URL = "https://anyforms.ru/orders/without-tracker";
    private static final String BUTTON_TEXT = "Заказы (anyforms)";
    private static final String DIVIDER = "\n\n";
    private static final int MAX_TEXT_LENGTH = 3500;

    private final OrderRepository orderRepository;
    private final TelegramNotificationRepository notificationRepository;

    @Override
    @Transactional
    public TelegramDigestDTO buildPendingDigest() {
        List<TelegramNotification> notifications = notificationRepository.findAllByOrderByIdAsc();
        if (notifications.isEmpty()) {
            return new TelegramDigestDTO(List.of(), null, null, null);
        }

        List<Long> queuedOrderIds = notifications.stream().map(TelegramNotification::getOrderId).toList();
        Map<Long, Order> ordersById = orderRepository.findAllById(queuedOrderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));

        List<Long> staleIds = queuedOrderIds.stream().filter(id -> !ordersById.containsKey(id)).toList();
        if (!staleIds.isEmpty()) {
            log.warn("Telegram queue: заказы {} не найдены, уведомления удалены", staleIds);
            notificationRepository.deleteByOrderIdIn(staleIds);
        }

        List<Order> pending = queuedOrderIds.stream()
                .map(ordersById::get)
                .filter(Objects::nonNull)
                .toList();
        if (pending.isEmpty()) {
            return new TelegramDigestDTO(List.of(), null, null, null);
        }

        List<Long> orderIds = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Order order : pending) {
            String block = buildOrderBlock(order);
            if (!orderIds.isEmpty() && sb.length() + DIVIDER.length() + block.length() > MAX_TEXT_LENGTH) {
                break;
            }
            if (!orderIds.isEmpty()) {
                sb.append(DIVIDER);
            }
            sb.append(block);
            orderIds.add(order.getId());
        }

        sb.append("\n\nСейчас в рознице:\n");
        sb.append("  • ждут отправки: ").append(orderRepository.countRetailAwaitingShipment()).append("\n");
        sb.append("  • в доставке: ").append(orderRepository.countRetailInDelivery());

        log.info("Telegram digest built: {} order(s) of {} pending", orderIds.size(), pending.size());
        return new TelegramDigestDTO(orderIds, sb.toString(), BUTTON_TEXT, ORDERS_WITHOUT_TRACKER_URL);
    }

    @Override
    @Transactional
    public int confirmSent(List<Long> orderIds) {
        int confirmed = notificationRepository.deleteByOrderIdIn(orderIds);
        log.info("Telegram digest confirmed: {} order(s)", confirmed);
        return confirmed;
    }

    private String buildOrderBlock(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🆕 Новый заказ в рознице №");
        sb.append(order.getPublicId() != null ? order.getPublicId() : order.getId());
        sb.append("\n");

        sb.append("Получатель: ").append(orDash(order.getContactName())).append("\n");
        sb.append("ПВЗ город: ").append(orDash(order.getPvzSdekCity())).append("\n");
        sb.append("ПВЗ улица: ").append(orDash(order.getPvzSdekStreet())).append("\n");
        List<OrderItem> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            sb.append("Состав:\n");
            for (OrderItem item : items) {
                sb.append("  • ").append(item.getProductName());
                if (item.getQuantity() != null && item.getQuantity() > 1) {
                    sb.append(" ×").append(item.getQuantity());
                }
                sb.append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
