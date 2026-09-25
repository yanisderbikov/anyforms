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
import ru.anyforms.repository.SaverOrder;
import ru.anyforms.service.task.TaskAdder;

@Log4j2
@Component
@RequiredArgsConstructor
class DeliveryEmailQueuer {

    private final TaskAdder taskAdder;
    private final SaverOrder saverOrder;

    @Transactional
    public void queue(Order order, DeliveryNotification notification, String tracker, CdekDeliveryEta eta) {
        String to = order.getEmail();
        Shop shop = order.getShop();
        taskAdder.addTask(DeliveryStatusEmailPayload.builder()
                .to(to)
                .notification(notification)
                .orderPublicId(order.getPublicId())
                .customerName(order.getContactName())
                .tracker(tracker)
                .deliveryEta(eta != null ? eta.describe() : null)
                .pvzCity(order.getPvzSdekCity())
                .pvzStreet(order.getPvzSdekStreet())
                .supportTelegram(shop != null ? shop.getSupportTelegram() : Shop.DEFAULT_SUPPORT_TELEGRAM)
                .shopSlug(shop != null ? shop.getSlug() : Shop.DEFAULT_SLUG)
                .shopName(shop != null ? shop.getName() : Shop.DEFAULT_SLUG)
                .build());
        order.setLastDeliveryNotification(notification);
        saverOrder.save(order);
        log.info("Delivery email {} queued for order #{} to {}", notification, order.getId(), to);
    }
}
