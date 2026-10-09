package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.Order;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.service.DeliveryBotNotifier;
import ru.anyforms.service.DeliveryEtaResolver;
import ru.anyforms.service.DeliveryNotifier;

@Log4j2
@Service
@RequiredArgsConstructor
class DeliveryNotifierImpl implements DeliveryNotifier {

    private final DeliveryBotNotifier deliveryBotNotifier;
    private final DeliveryEmailQueuer deliveryEmailQueuer;
    private final DeliveryEtaResolver deliveryEtaResolver;
    private final AmoCrmGateway amoCrmGateway;

    @Override
    public void notifyShipped(Order order, String tracker) {
        if (!order.isRetail()) {
            deliveryBotNotifier.notifyShipped(order.getLeadId(), tracker, deliveryEta(order, tracker));
            return;
        }
        String to = emailFor(order, DeliveryNotification.SHIPPED);
        if (to != null) {
            deliveryEmailQueuer.queue(order, to, DeliveryNotification.SHIPPED, tracker, deliveryEta(order, tracker));
        }
    }

    @Override
    public void notifyArrivedAtPvz(Order order) {
        if (!order.isRetail()) {
            deliveryBotNotifier.notifyCdekReadyToPickup(order.getLeadId());
            return;
        }
        String to = emailFor(order, DeliveryNotification.ARRIVED_AT_PVZ);
        if (to != null) {
            deliveryEmailQueuer.queue(order, to, DeliveryNotification.ARRIVED_AT_PVZ, order.getTracker(), null);
        }
    }

    @Override
    public void notifyReadyForPickup(Order order) {
        if (!order.isRetail()) {
            deliveryBotNotifier.notifyPickupReady(order.getLeadId());
            return;
        }
        String to = emailFor(order, DeliveryNotification.READY_FOR_PICKUP);
        if (to != null) {
            deliveryEmailQueuer.queue(order, to, DeliveryNotification.READY_FOR_PICKUP, null, null);
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

    private String emailFor(Order order, DeliveryNotification notification) {
        DeliveryNotification last = order.getLastDeliveryNotification();
        if (last != null && last.compareTo(notification) >= 0) {
            log.info("Delivery email {} skipped for order #{}: already notified {}", notification, order.getId(), last);
            return null;
        }
        if (!isBlank(order.getEmail())) {
            return order.getEmail();
        }
        String fromAmo = emailFromAmo(order);
        if (isBlank(fromAmo)) {
            log.warn("Order #{} has no email, delivery notification {} not sent", order.getId(), notification);
            return null;
        }
        log.info("Email for order #{} taken from AmoCRM contact of lead {}", order.getId(), order.getLeadId());
        return fromAmo;
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
}
