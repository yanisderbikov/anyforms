package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.dto.email.DeliveryStatusEmailPayload;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.Order;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.SaverOrder;
import ru.anyforms.service.DeliveryBotNotifier;
import ru.anyforms.service.DeliveryEtaResolver;
import ru.anyforms.service.task.TaskAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryNotifierImplTest {

    private final DeliveryBotNotifier deliveryBotNotifier = mock(DeliveryBotNotifier.class);
    private final TaskAdder taskAdder = mock(TaskAdder.class);
    private final SaverOrder saverOrder = mock(SaverOrder.class);
    private final DeliveryEtaResolver deliveryEtaResolver = mock(DeliveryEtaResolver.class);
    private final DeliveryNotifierImpl notifier = new DeliveryNotifierImpl(deliveryBotNotifier, taskAdder, saverOrder, deliveryEtaResolver);

    private static Order order(boolean retail) {
        Order order = new Order();
        order.setId(5L);
        order.setLeadId(777L);
        order.setRetail(retail);
        order.setEmail("buyer@mail.ru");
        order.setPublicId("ab12cd");
        order.setContactName("Иван");
        order.setPvzSdekCity("Москва");
        order.setPvzSdekStreet("ул. Ленина, 1");
        order.setTracker("1234567890");
        return order;
    }

    private DeliveryStatusEmailPayload emailTask() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(taskAdder).addTask(captor.capture());
        return (DeliveryStatusEmailPayload) captor.getValue();
    }

    @Test
    void retailShippedGoesToEmailNotBot() {
        Order order = order(true);
        Shop shop = new Shop();
        shop.setSlug("af_pastry");
        shop.setName("AF Pastry");
        shop.setSupportTelegram("AfPastryBot");
        order.setShop(shop);

        notifier.notifyShipped(order, "1234567890");

        DeliveryStatusEmailPayload payload = emailTask();
        assertEquals("buyer@mail.ru", payload.getTo());
        assertEquals(DeliveryNotification.SHIPPED, payload.getNotification());
        assertEquals("1234567890", payload.getTracker());
        assertEquals("ab12cd", payload.getOrderPublicId());
        assertEquals("AfPastryBot", payload.getSupportTelegram());
        assertEquals("af_pastry", payload.getShopSlug());
        assertEquals(DeliveryNotification.SHIPPED, order.getLastDeliveryNotification());
        verify(saverOrder).save(order);
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void retailShippedCarriesDeliveryEtaFromCdek() {
        Order order = order(true);
        when(deliveryEtaResolver.resolve("1234567890")).thenReturn(CdekDeliveryEta.ofPeriod(2, 3));

        notifier.notifyShipped(order, "1234567890");

        assertEquals("2-3 дня", emailTask().getDeliveryEta());
    }

    @Test
    void retailShippedWithoutEtaStillGoesOut() {
        Order order = order(true);
        when(deliveryEtaResolver.resolve("1234567890")).thenThrow(new RuntimeException("cdek down"));

        notifier.notifyShipped(order, "1234567890");

        assertNull(emailTask().getDeliveryEta());
    }

    @Test
    void etaIsNotRequestedWhenEmailIsSkipped() {
        Order order = order(true);
        order.setLastDeliveryNotification(DeliveryNotification.SHIPPED);

        notifier.notifyShipped(order, "1234567890");

        verifyNoInteractions(deliveryEtaResolver);
    }

    @Test
    void retailArrivedUsesOrderTrackerAndDefaultSupportWithoutShop() {
        Order order = order(true);

        notifier.notifyArrivedAtPvz(order);

        DeliveryStatusEmailPayload payload = emailTask();
        assertEquals(DeliveryNotification.ARRIVED_AT_PVZ, payload.getNotification());
        assertEquals("1234567890", payload.getTracker());
        assertEquals(Shop.DEFAULT_SUPPORT_TELEGRAM, payload.getSupportTelegram());
        assertEquals(Shop.DEFAULT_SLUG, payload.getShopSlug());
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void retailReadyForPickupGoesToEmail() {
        Order order = order(true);

        notifier.notifyReadyForPickup(order);

        assertEquals(DeliveryNotification.READY_FOR_PICKUP, emailTask().getNotification());
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void sameNotificationIsNotSentTwice() {
        Order order = order(true);
        order.setLastDeliveryNotification(DeliveryNotification.SHIPPED);

        notifier.notifyShipped(order, "1234567890");

        verify(taskAdder, never()).addTask(any());
        verify(saverOrder, never()).save(any());
    }

    @Test
    void earlierStageIsNotSentAfterLaterOne() {
        Order order = order(true);
        order.setLastDeliveryNotification(DeliveryNotification.ARRIVED_AT_PVZ);

        notifier.notifyShipped(order, "1234567890");

        verify(taskAdder, never()).addTask(any());
        assertEquals(DeliveryNotification.ARRIVED_AT_PVZ, order.getLastDeliveryNotification());
    }

    @Test
    void nextStageIsSentAfterPreviousOne() {
        Order order = order(true);
        order.setLastDeliveryNotification(DeliveryNotification.SHIPPED);

        notifier.notifyArrivedAtPvz(order);

        assertEquals(DeliveryNotification.ARRIVED_AT_PVZ, emailTask().getNotification());
        assertEquals(DeliveryNotification.ARRIVED_AT_PVZ, order.getLastDeliveryNotification());
    }

    @Test
    void retailWithoutEmailSendsNothing() {
        Order order = order(true);
        order.setEmail(null);

        notifier.notifyShipped(order, "1234567890");

        verify(taskAdder, never()).addTask(any());
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void customOrdersStillGoToBots() {
        Order order = order(false);
        when(deliveryEtaResolver.resolve("1234567890")).thenReturn(CdekDeliveryEta.ofPeriod(2, 2));

        notifier.notifyShipped(order, "1234567890");
        notifier.notifyArrivedAtPvz(order);
        notifier.notifyReadyForPickup(order);

        ArgumentCaptor<CdekDeliveryEta> etaCaptor = ArgumentCaptor.forClass(CdekDeliveryEta.class);
        verify(deliveryBotNotifier).notifyShipped(eq(777L), eq("1234567890"), etaCaptor.capture());
        assertNotNull(etaCaptor.getValue());
        assertEquals("2 дня", etaCaptor.getValue().daysText());
        verify(deliveryBotNotifier).notifyCdekReadyToPickup(777L);
        verify(deliveryBotNotifier).notifyPickupReady(777L);
        verify(taskAdder, never()).addTask(any());
    }
}
