package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.dto.email.DeliveryStatusEmailPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.Order;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.DeliveryBotNotifier;
import ru.anyforms.service.DeliveryEtaResolver;
import ru.anyforms.service.task.TaskAdder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryNotifierImplTest {

    private static final long ORDER_ID = 5L;

    private final DeliveryBotNotifier deliveryBotNotifier = mock(DeliveryBotNotifier.class);
    private final TaskAdder taskAdder = mock(TaskAdder.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final DeliveryEtaResolver deliveryEtaResolver = mock(DeliveryEtaResolver.class);
    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final DeliveryNotifierImpl notifier = new DeliveryNotifierImpl(deliveryBotNotifier,
            new DeliveryEmailQueuer(taskAdder, orderRepository), deliveryEtaResolver, amoCrmGateway);

    private static AmoContact contactWithEmail(String value) {
        AmoContact.Email email = new AmoContact.Email();
        email.setValue(value);
        AmoContact contact = new AmoContact();
        contact.setEmail(List.of(email));
        return contact;
    }

    private Order order(boolean retail) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setLeadId(777L);
        order.setRetail(retail);
        order.setEmail("buyer@mail.ru");
        order.setPublicId("ab12cd");
        order.setContactName("Иван");
        order.setPvzSdekCity("Москва");
        order.setPvzSdekStreet("ул. Ленина, 1");
        order.setTracker("1234567890");
        when(orderRepository.claimFirstDeliveryNotification(eq(ORDER_ID), anyString())).thenReturn(1);
        when(orderRepository.claimNextDeliveryNotification(eq(ORDER_ID), anyString(), any())).thenReturn(1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        return order;
    }

    private DeliveryStatusEmailPayload emailTask() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(taskAdder).addTaskOrThrow(captor.capture());
        return (DeliveryStatusEmailPayload) captor.getValue();
    }

    private void verifyNothingQueued() {
        verify(taskAdder, never()).addTaskOrThrow(any());
        verify(taskAdder, never()).addTask(any());
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
        verify(orderRepository).claimFirstDeliveryNotification(ORDER_ID, "SHIPPED");
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void shopAndFreeDeliveryAreReadFromTheOrderLoadedInsideTheTransaction() {
        Order detached = order(true);
        Order managed = new Order();
        managed.setId(ORDER_ID);
        managed.setEmail("buyer@mail.ru");
        managed.setPublicId("ab12cd");
        managed.setFreeDelivery(true);
        Shop shop = new Shop();
        shop.setSlug("lunasvecha");
        shop.setName("Луна Свеча");
        shop.setSupportTelegram("LunaBot");
        managed.setShop(shop);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(managed));

        notifier.notifyShipped(detached, "1234567890");

        DeliveryStatusEmailPayload payload = emailTask();
        assertEquals("lunasvecha", payload.getShopSlug());
        assertEquals("LunaBot", payload.getSupportTelegram());
        assertTrue(payload.isFreeDelivery());
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
        verify(orderRepository).claimNextDeliveryNotification(ORDER_ID, "ARRIVED_AT_PVZ", List.of("SHIPPED"));
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void retailReadyForPickupGoesToEmail() {
        Order order = order(true);

        notifier.notifyReadyForPickup(order);

        assertEquals(DeliveryNotification.READY_FOR_PICKUP, emailTask().getNotification());
        verify(orderRepository).claimNextDeliveryNotification(ORDER_ID, "READY_FOR_PICKUP", List.of("SHIPPED", "ARRIVED_AT_PVZ"));
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void sameNotificationIsNotSentTwice() {
        Order order = order(true);
        order.setLastDeliveryNotification(DeliveryNotification.SHIPPED);

        notifier.notifyShipped(order, "1234567890");

        verifyNothingQueued();
        verify(orderRepository, never()).claimFirstDeliveryNotification(anyLong(), anyString());
    }

    @Test
    void earlierStageIsNotSentAfterLaterOne() {
        Order order = order(true);
        order.setLastDeliveryNotification(DeliveryNotification.ARRIVED_AT_PVZ);

        notifier.notifyShipped(order, "1234567890");

        verifyNothingQueued();
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
    void concurrentRunThatLosesTheClaimSendsNothing() {
        Order order = order(true);
        when(orderRepository.claimFirstDeliveryNotification(eq(ORDER_ID), anyString())).thenReturn(0);

        notifier.notifyShipped(order, "1234567890");

        verifyNothingQueued();
        assertNull(order.getLastDeliveryNotification());
        verify(orderRepository, never()).findById(anyLong());
    }

    @Test
    void taskPersistenceFailurePropagatesSoTheClaimRollsBack() {
        Order order = order(true);
        doThrow(new RuntimeException("db down")).when(taskAdder).addTaskOrThrow(any());

        assertThrows(RuntimeException.class, () -> notifier.notifyShipped(order, "1234567890"));

        assertNull(order.getLastDeliveryNotification());
    }

    @Test
    void retailWithoutEmailSendsNothing() {
        Order order = order(true);
        order.setEmail(null);

        notifier.notifyShipped(order, "1234567890");

        verify(amoCrmGateway).getContactFromLead(777L);
        verifyNothingQueued();
        verify(orderRepository, never()).claimFirstDeliveryNotification(anyLong(), anyString());
        verifyNoInteractions(deliveryBotNotifier);
    }

    @Test
    void orderEmailWinsOverAmo() {
        Order order = order(true);

        notifier.notifyShipped(order, "1234567890");

        assertEquals("buyer@mail.ru", emailTask().getTo());
        verifyNoInteractions(amoCrmGateway);
    }

    @Test
    void missingOrderEmailIsTakenFromAmoContactAndSaved() {
        Order order = order(true);
        order.setEmail(" ");
        when(amoCrmGateway.getContactFromLead(777L)).thenReturn(contactWithEmail("amo@mail.ru"));

        notifier.notifyArrivedAtPvz(order);

        assertEquals("amo@mail.ru", emailTask().getTo());
        assertEquals("amo@mail.ru", order.getEmail());
    }

    @Test
    void amoEmailIsWrittenToTheManagedOrderOnlyWhenItHasNone() {
        Order detached = order(true);
        detached.setEmail(null);
        Order managed = new Order();
        managed.setId(ORDER_ID);
        managed.setPublicId("ab12cd");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(managed));
        when(amoCrmGateway.getContactFromLead(777L)).thenReturn(contactWithEmail("amo@mail.ru"));

        notifier.notifyShipped(detached, "1234567890");

        assertEquals("amo@mail.ru", emailTask().getTo());
        assertEquals("amo@mail.ru", managed.getEmail());
        assertEquals("amo@mail.ru", detached.getEmail());
    }

    @Test
    void managedOrderEmailIsNotOverwritten() {
        Order detached = order(true);
        Order managed = new Order();
        managed.setId(ORDER_ID);
        managed.setPublicId("ab12cd");
        managed.setEmail("shop@mail.ru");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(managed));

        notifier.notifyShipped(detached, "1234567890");

        assertEquals("buyer@mail.ru", emailTask().getTo());
        assertEquals("shop@mail.ru", managed.getEmail());
    }

    @Test
    void amoEmailIsKeptOnTheOrderEvenWhenTheClaimIsLost() {
        Order order = order(true);
        order.setEmail(null);
        when(amoCrmGateway.getContactFromLead(777L)).thenReturn(contactWithEmail("amo@mail.ru"));
        when(orderRepository.claimFirstDeliveryNotification(eq(ORDER_ID), anyString())).thenReturn(0);

        notifier.notifyShipped(order, "1234567890");

        verifyNothingQueued();
        assertEquals("amo@mail.ru", order.getEmail());
    }

    @Test
    void amoFailureMeansNoEmail() {
        Order order = order(true);
        order.setEmail(null);
        when(amoCrmGateway.getContactFromLead(777L)).thenThrow(new RuntimeException("amo down"));

        notifier.notifyShipped(order, "1234567890");

        verifyNothingQueued();
        assertNull(order.getEmail());
    }

    @Test
    void amoIsNotAskedWithoutLeadOrWhenAlreadyNotified() {
        Order withoutLead = order(true);
        withoutLead.setEmail(null);
        withoutLead.setLeadId(null);
        Order notified = order(true);
        notified.setEmail(null);
        notified.setLastDeliveryNotification(DeliveryNotification.SHIPPED);

        notifier.notifyShipped(withoutLead, "1234567890");
        notifier.notifyShipped(notified, "1234567890");

        verifyNoInteractions(amoCrmGateway);
        verifyNothingQueued();
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
        verifyNothingQueued();
        verify(orderRepository, never()).claimFirstDeliveryNotification(anyLong(), anyString());
    }
}
