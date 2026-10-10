package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.dto.email.DeliveryStatusEmailPayload;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.Order;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.task.TaskAdder;

import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
class DeliveryEmailQueuer {

    private final TaskAdder taskAdder;
    private final OrderRepository orderRepository;

    @Transactional
    public boolean queue(Order order, String to, DeliveryNotification notification, String tracker, CdekDeliveryEta eta) {
        if (!claim(order.getId(), notification)) {
            log.info("Delivery email {} skipped for order #{}: already claimed by another run", notification, order.getId());
            return false;
        }
        Order managed = orderRepository.findById(order.getId())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + order.getId()));
        if (managed.getEmail() == null || managed.getEmail().isBlank()) {
            managed.setEmail(to);
        }
        Shop shop = managed.getShop();
        taskAdder.addTaskOrThrow(DeliveryStatusEmailPayload.builder()
                .to(to)
                .notification(notification)
                .orderPublicId(managed.getPublicId())
                .customerName(managed.getContactName())
                .tracker(tracker)
                .deliveryEta(eta != null ? eta.describe() : null)
                .pvzCity(managed.getPvzSdekCity())
                .pvzStreet(managed.getPvzSdekStreet())
                .supportTelegram(shop != null ? shop.getSupportTelegram() : Shop.DEFAULT_SUPPORT_TELEGRAM)
                .shopSlug(shop != null ? shop.getSlug() : Shop.DEFAULT_SLUG)
                .shopName(shop != null ? shop.getName() : Shop.DEFAULT_SLUG)
                .freeDelivery(managed.isFreeDelivery())
                .build());
        order.setLastDeliveryNotification(notification);
        order.setEmail(to);
        log.info("Delivery email {} queued for order #{}", notification, order.getId());
        return true;
    }

    private boolean claim(Long orderId, DeliveryNotification notification) {
        List<String> earlier = notification.earlierNames();
        int claimed = earlier.isEmpty()
                ? orderRepository.claimFirstDeliveryNotification(orderId, notification.name())
                : orderRepository.claimNextDeliveryNotification(orderId, notification.name(), earlier);
        return claimed > 0;
    }
}
