package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.dto.email.DeliveryStatusEmailPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.Order;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.SaverOrder;
import ru.anyforms.service.DeliveryBotNotifier;
import ru.anyforms.service.DeliveryEtaResolver;
import ru.anyforms.service.DeliveryNotifier;
import ru.anyforms.service.task.TaskAdder;

@Log4j2
@Service
@RequiredArgsConstructor
class DeliveryNotifierImpl implements DeliveryNotifier {

    private final DeliveryBotNotifier deliveryBotNotifier;
    private final TaskAdder taskAdder;
    private final SaverOrder saverOrder;
    private final DeliveryEtaResolver deliveryEtaResolver;
    private final AmoCrmGateway amoCrmGateway;

    @Override
    public void notifyShipped(Order order, String tracker) {
        if (order.isRetail()) {
            if (canSendEmail(order, DeliveryNotification.SHIPPED)) {
                sendEmail(order, DeliveryNotification.SHIPPED, tracker, deliveryEta(order, tracker));
            }
        } else {
            deliveryBotNotifier.notifyShipped(order.getLeadId(), tracker, deliveryEta(order, tracker));
        }
    }

    @Override
    public void notifyArrivedAtPvz(Order order) {
        if (order.isRetail()) {
            if (canSendEmail(order, DeliveryNotification.ARRIVED_AT_PVZ)) {
                sendEmail(order, DeliveryNotification.ARRIVED_AT_PVZ, order.getTracker(), null);
            }
        } else {
            deliveryBotNotifier.notifyCdekReadyToPickup(order.getLeadId());
        }
    }

    @Override
    public void notifyReadyForPickup(Order order) {
        if (order.isRetail()) {
            if (canSendEmail(order, DeliveryNotification.READY_FOR_PICKUP)) {
                sendEmail(order, DeliveryNotification.READY_FOR_PICKUP, null, null);
            }
        } else {
            deliveryBotNotifier.notifyPickupReady(order.getLeadId());
        }
    }

    private CdekDeliveryEta deliveryEta(Order order, String tracker) {
        try {
            CdekDeliveryEta eta = deliveryEtaResolver.resolve(tracker);
            if (eta == null) {
                log.info("No delivery ETA from CDEK for order #{} (tracker {})", order.getId(), tracker);
            }
            return eta;
        } catch (Exception e) {
            log.error("Failed to get delivery ETA for order #{} (tracker {}): {}", order.getId(), tracker, e.getMessage(), e);
            return null;
        }
    }

    private boolean canSendEmail(Order order, DeliveryNotification notification) {
        DeliveryNotification last = order.getLastDeliveryNotification();
        if (last != null && last.compareTo(notification) >= 0) {
            log.info("Delivery email {} skipped for order #{}: already notified {}", notification, order.getId(), last);
            return false;
        }
        if (isBlank(order.getEmail())) {
            String fromAmo = emailFromAmo(order);
            if (isBlank(fromAmo)) {
                log.warn("Order #{} has no email, delivery notification {} not sent", order.getId(), notification);
                return false;
            }
            order.setEmail(fromAmo);
            log.info("Email for order #{} taken from AmoCRM contact of lead {}", order.getId(), order.getLeadId());
        }
        return true;
    }

    private String emailFromAmo(Order order) {
        if (order.getLeadId() == null) {
            return null;
        }
        try {
            AmoContact contact = amoCrmGateway.getContactFromLead(order.getLeadId());
            return contact != null ? contact.getDefaultEmail() : null;
        } catch (Exception e) {
            log.error("Failed to get email from AmoCRM for order #{} (lead {}): {}", order.getId(), order.getLeadId(), e.getMessage(), e);
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void sendEmail(Order order, DeliveryNotification notification, String tracker, CdekDeliveryEta eta) {
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
