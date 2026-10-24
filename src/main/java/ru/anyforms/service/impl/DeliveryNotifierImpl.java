package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.Order;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.OrderRepository;
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
    private final OrderRepository orderRepository;

    @Override
    public void notifyShipped(Order order, String tracker) {
        boolean bot = notifiesByBot(order);
        String to = order.isRetail() ? emailFor(order, DeliveryNotification.SHIPPED) : null;
        if (!bot && to == null) {
            return;
        }
        CdekDeliveryEta eta = deliveryEta(order, tracker);
        if (bot) {
            deliveryBotNotifier.notifyShipped(order.getLeadId(), tracker, eta);
        }
        if (to != null) {
            deliveryEmailQueuer.queue(order, to, DeliveryNotification.SHIPPED, tracker, eta);
        }
    }

    @Override
    public void notifyArrivedAtPvz(Order order) {
        if (notifiesByBot(order)) {
            deliveryBotNotifier.notifyCdekReadyToPickup(order.getLeadId());
        }
        if (!order.isRetail()) {
            return;
        }
        String to = emailFor(order, DeliveryNotification.ARRIVED_AT_PVZ);
        if (to != null) {
            deliveryEmailQueuer.queue(order, to, DeliveryNotification.ARRIVED_AT_PVZ, order.getTracker(), null);
        }
    }

    @Override
    public void notifyReadyForPickup(Order order) {
        if (notifiesByBot(order)) {
            deliveryBotNotifier.notifyPickupReady(order.getLeadId());
        }
        if (!order.isRetail()) {
            return;
        }
        String to = emailFor(order, DeliveryNotification.READY_FOR_PICKUP);
        if (to != null) {
            deliveryEmailQueuer.queue(order, to, DeliveryNotification.READY_FOR_PICKUP, null, null);
        }
    }

    private boolean notifiesByBot(Order order) {
        if (!order.isRetail()) {
            return true;
        }
        String slug = order.getId() != null ? orderRepository.findShopSlug(order.getId()).orElse(null) : null;
        return slug == null || Shop.DEFAULT_SLUG.equals(slug);
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
        order.setEmail(fromAmo);
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
