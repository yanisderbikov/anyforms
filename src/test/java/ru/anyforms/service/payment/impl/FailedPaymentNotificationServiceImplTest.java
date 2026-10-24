package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.OrderRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailedPaymentNotificationServiceImplTest {

    private static final long EDUCATION_PIPELINE_ID = FailedPaymentNotificationServiceImpl.EDUCATION_PIPELINE_ID;
    private static final long EDUCATION_FAILED_STATUS_ID = FailedPaymentNotificationServiceImpl.EDUCATION_FAILED_STATUS_ID;
    private static final long MARKETPLACE_FAILED_PIPELINE_ID = FailedPaymentNotificationServiceImpl.MARKETPLACE_FAILED_PIPELINE_ID;
    private static final long MARKETPLACE_FAILED_STATUS_ID = FailedPaymentNotificationServiceImpl.MARKETPLACE_FAILED_STATUS_ID;
    private static final long IRINA_ID = 13161462L;
    private static final long LOST_MESSAGE_TASK_TYPE_ID = 3986070L;
    private static final int ONE_DAY_MINUTES = 24 * 60;

    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final FailedPaymentNotificationServiceImpl service =
            new FailedPaymentNotificationServiceImpl(amoCrmGateway, new AmoContactFinder(amoCrmGateway), orderRepository);

    private static PaymentTransaction transaction(String productCode, String name, String phone, String email) {
        return PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .productCode(productCode)
                .contactName(name)
                .contactPhone(phone)
                .email(email)
                .build();
    }

    private void noExistingContactInAmo() {
        when(amoCrmGateway.findContactIdByQuery(anyString())).thenReturn(null);
    }

    private static PaymentTransaction marketplaceTransaction(Long orderId) {
        PaymentTransaction transaction = transaction(PaymentProduct.CODE_MARKETPLACE_CART,
                "Иванов Иван", "+79001234567", "buyer@mail.ru");
        transaction.setOrderId(orderId);
        return transaction;
    }

    private static OrderItem item(String name, Integer quantity) {
        OrderItem item = new OrderItem();
        item.setProductName(name);
        item.setQuantity(quantity);
        return item;
    }

    private Order orderWithItems(long orderId, OrderItem... items) {
        Order order = new Order();
        order.setId(orderId);
        order.setPublicId("AF-42");
        order.getItems().addAll(List.of(items));
        when(orderRepository.findByIdWithShop(orderId)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void guideFailureCreatesEducationLeadWithContactDataAndLostMessageTask() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead("Неуспешная оплата - GUIDE", "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID, IRINA_ID)).thenReturn(555L);
        when(amoCrmGateway.getContactIdFromLead(555L)).thenReturn(777L);

        service.notify(transaction(PaymentProduct.CODE_GUIDE, "Иванов Иван", "+79001234567", "buyer@mail.ru"));

        verify(amoCrmGateway).createLead("Неуспешная оплата - GUIDE", "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway).updateContactCustomField(777L,
                Map.of(AmoCrmFieldId.FIO_CONTACT.getId(), "Иванов Иван"));
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.TASK_TEXT, 555L, ONE_DAY_MINUTES);
        verify(amoCrmGateway).updateContactResponsible(777L, IRINA_ID);
    }

    @Test
    void courseAndPersonalCourseGoToEducationPipeline() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(555L);

        service.notify(transaction(PaymentProduct.CODE_COURSE, "Иванов Иван", null, "buyer@mail.ru"));
        service.notify(transaction(PaymentProduct.CODE_COURSE_PERSONAL, "Иванов Иван", null, "buyer@mail.ru"));

        verify(amoCrmGateway).createLead("Неуспешная оплата - COURSE", "Иванов Иван", null,
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway).createLead("Неуспешная оплата - COURSE_PERSONAL", "Иванов Иван", null,
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID, IRINA_ID);
    }

    @Test
    void marketplaceFailureCreatesRetailLeadNamedAfterOrderItems() {
        noExistingContactInAmo();
        orderWithItems(42L, item("Свеча Луна", 1), item("Подсвечник", 2));
        String leadName = "Неудачная оплата Розницы - anyforms - Свеча Луна, Подсвечник ×2";
        when(amoCrmGateway.createLead(leadName, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", MARKETPLACE_FAILED_PIPELINE_ID, MARKETPLACE_FAILED_STATUS_ID, IRINA_ID))
                .thenReturn(555L);
        when(amoCrmGateway.getContactIdFromLead(555L)).thenReturn(777L);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).createLead(leadName, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", MARKETPLACE_FAILED_PIPELINE_ID, 83287002L, IRINA_ID);
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.MARKETPLACE_TASK_TEXT, 555L, ONE_DAY_MINUTES);
        verify(amoCrmGateway).addNoteToLead(555L,
                "Не получилось оплатить в магазине anyforms заказ AF-42:\n— Свеча Луна × 1\n— Подсвечник × 2");
        verify(amoCrmGateway).updateContactResponsible(777L, IRINA_ID);
    }

    @Test
    void marketplaceFailureNamesLeadAfterOrderShop() {
        noExistingContactInAmo();
        Order order = orderWithItems(42L, item("Гипсовая ваза", 1));
        order.setShop(ru.anyforms.model.marketplace.Shop.builder().slug("di_gips").name("DI Gips").build());
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(555L);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).createLead("Неудачная оплата Розницы - di_gips - Гипсовая ваза", "Иванов Иван", "+79001234567",
                "buyer@mail.ru", MARKETPLACE_FAILED_PIPELINE_ID, MARKETPLACE_FAILED_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway).addNoteToLead(555L,
                "Не получилось оплатить в магазине di_gips заказ AF-42:\n— Гипсовая ваза × 1");
    }

    @Test
    void marketplaceFailureTakesContactNameAndPhoneFromOrderWhenTransactionHasNone() {
        noExistingContactInAmo();
        Order order = new Order();
        order.setId(42L);
        order.setPublicId("AF-42");
        order.setContactName("Петрова Анна");
        order.setContactPhone("+79007654321");
        order.getItems().add(item("Свеча Луна", 1));
        when(orderRepository.findByIdWithShop(42L)).thenReturn(Optional.of(order));
        PaymentTransaction transaction = transaction(PaymentProduct.CODE_MARKETPLACE_CART, null, null, "buyer@mail.ru");
        transaction.setOrderId(42L);
        String leadName = "Неудачная оплата Розницы - anyforms - Свеча Луна";
        when(amoCrmGateway.createLead(leadName, "Петрова Анна", "+79007654321",
                "buyer@mail.ru", MARKETPLACE_FAILED_PIPELINE_ID, MARKETPLACE_FAILED_STATUS_ID, IRINA_ID))
                .thenReturn(555L);
        when(amoCrmGateway.getContactIdFromLead(555L)).thenReturn(777L);

        service.notify(transaction);

        verify(amoCrmGateway).findContactIdByQuery("79007654321");
        verify(amoCrmGateway).createLead(leadName, "Петрова Анна", "+79007654321",
                "buyer@mail.ru", MARKETPLACE_FAILED_PIPELINE_ID, MARKETPLACE_FAILED_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway).updateContactCustomField(777L,
                Map.of(AmoCrmFieldId.FIO_CONTACT.getId(), "Петрова Анна"));
    }

    @Test
    void marketplaceFailureWithoutOrderStillCreatesLead() {
        noExistingContactInAmo();
        when(orderRepository.findByIdWithShop(42L)).thenReturn(Optional.empty());
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(555L);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).createLead("Неудачная оплата Розницы - anyforms - заказ не найден", "Иванов Иван", "+79001234567",
                "buyer@mail.ru", MARKETPLACE_FAILED_PIPELINE_ID, MARKETPLACE_FAILED_STATUS_ID, IRINA_ID);
    }

    @Test
    void marketplaceRepeatedFailureAddsRetailTaskToExistingLead() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.getLeadIdsByContact(777L)).thenReturn(List.of(555L));
        ru.anyforms.model.amo.AmoLead lead = new ru.anyforms.model.amo.AmoLead();
        lead.setPipelineId(MARKETPLACE_FAILED_PIPELINE_ID);
        when(amoCrmGateway.getLead(555L)).thenReturn(lead);
        when(amoCrmGateway.hasIncompleteTask(555L)).thenReturn(false);
        orderWithItems(42L, item("Свеча Луна", 1));

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.MARKETPLACE_TASK_TEXT, 555L, ONE_DAY_MINUTES);
        verify(amoCrmGateway).addNoteToLead(555L, "Не получилось оплатить в магазине anyforms заказ AF-42:\n— Свеча Луна × 1");
        verify(amoCrmGateway).updateLeadStatus(555L, MARKETPLACE_FAILED_STATUS_ID, MARKETPLACE_FAILED_PIPELINE_ID, IRINA_ID);
        verify(amoCrmGateway).updateContactResponsible(777L, IRINA_ID);
    }

    private ru.anyforms.model.amo.AmoLead existingRetailLead(Long statusId, Long responsibleUserId) {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.getLeadIdsByContact(777L)).thenReturn(List.of(555L));
        ru.anyforms.model.amo.AmoLead lead = new ru.anyforms.model.amo.AmoLead();
        lead.setPipelineId(MARKETPLACE_FAILED_PIPELINE_ID);
        lead.setStatusId(statusId);
        lead.setResponsibleUserId(responsibleUserId);
        when(amoCrmGateway.getLead(555L)).thenReturn(lead);
        return lead;
    }

    @Test
    void existingLeadAlreadyInFailedStatusOnManagerIsNotTouched() {
        existingRetailLead(MARKETPLACE_FAILED_STATUS_ID, IRINA_ID);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway, never()).updateLeadResponsible(anyLong(), anyLong());
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.MARKETPLACE_TASK_TEXT, 555L, ONE_DAY_MINUTES);
    }

    @Test
    void existingLeadInFailedStatusOnSomeoneElseOnlyChangesResponsible() {
        existingRetailLead(MARKETPLACE_FAILED_STATUS_ID, 99L);
        when(amoCrmGateway.hasIncompleteTask(555L)).thenReturn(true);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).updateLeadResponsible(555L, IRINA_ID);
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    @Test
    void existingLeadInOtherStatusIsMovedToFailedStatusAndManager() {
        existingRetailLead(12345L, IRINA_ID);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).updateLeadStatus(555L, MARKETPLACE_FAILED_STATUS_ID, MARKETPLACE_FAILED_PIPELINE_ID, IRINA_ID);
        verify(amoCrmGateway, never()).updateLeadResponsible(anyLong(), anyLong());
    }

    @Test
    void realizedLeadKeepsStatusAndResponsibleButGetsTaskAndNote() {
        existingRetailLead(142L, 99L);
        orderWithItems(42L, item("Свеча Луна", 1));

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway, never()).updateLeadResponsible(anyLong(), anyLong());
        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.MARKETPLACE_TASK_TEXT, 555L, ONE_DAY_MINUTES);
        verify(amoCrmGateway).addNoteToLead(555L, "Не получилось оплатить в магазине anyforms заказ AF-42:\n— Свеча Луна × 1");
    }

    @Test
    void closedLeadIsReopenedIntoFailedStatusInsteadOfCreatingNewOne() {
        existingRetailLead(143L, IRINA_ID);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).updateLeadStatus(555L, MARKETPLACE_FAILED_STATUS_ID, MARKETPLACE_FAILED_PIPELINE_ID, IRINA_ID);
        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.MARKETPLACE_TASK_TEXT, 555L, ONE_DAY_MINUTES);
    }

    @Test
    void openLeadIsPreferredOverClosedOne() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.getLeadIdsByContact(777L)).thenReturn(List.of(500L, 555L));
        ru.anyforms.model.amo.AmoLead closed = new ru.anyforms.model.amo.AmoLead();
        closed.setPipelineId(MARKETPLACE_FAILED_PIPELINE_ID);
        closed.setStatusId(143L);
        when(amoCrmGateway.getLead(500L)).thenReturn(closed);
        ru.anyforms.model.amo.AmoLead open = new ru.anyforms.model.amo.AmoLead();
        open.setPipelineId(MARKETPLACE_FAILED_PIPELINE_ID);
        open.setStatusId(12345L);
        when(amoCrmGateway.getLead(555L)).thenReturn(open);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).updateLeadStatus(555L, MARKETPLACE_FAILED_STATUS_ID, MARKETPLACE_FAILED_PIPELINE_ID, IRINA_ID);
        verify(amoCrmGateway, never()).updateLeadStatus(eq(500L), anyLong(), anyLong(), anyLong());
    }

    @Test
    void marketplaceRepeatedFailureWithOpenTaskStillAddsItemsNote() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.getLeadIdsByContact(777L)).thenReturn(List.of(555L));
        ru.anyforms.model.amo.AmoLead lead = new ru.anyforms.model.amo.AmoLead();
        lead.setPipelineId(MARKETPLACE_FAILED_PIPELINE_ID);
        when(amoCrmGateway.getLead(555L)).thenReturn(lead);
        when(amoCrmGateway.hasIncompleteTask(555L)).thenReturn(true);
        orderWithItems(42L, item("Подсвечник", 2));
        PaymentTransaction transaction = marketplaceTransaction(42L);
        transaction.setAmount(198_000L);

        service.notify(transaction);

        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
        verify(amoCrmGateway).addNoteToLead(555L,
                "Не получилось оплатить в магазине anyforms заказ AF-42 на " + ru.anyforms.util.MoneyUtil.formatRubles(198_000L)
                        + ":\n— Подсвечник × 2");
    }

    @Test
    void marketplaceFailureWithoutOrderAddsNoteWithoutItems() {
        noExistingContactInAmo();
        when(orderRepository.findByIdWithShop(42L)).thenReturn(Optional.empty());
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(555L);

        service.notify(marketplaceTransaction(42L));

        verify(amoCrmGateway).addNoteToLead(555L, "Не получилось оплатить в магазине anyforms:\n— состав заказа не найден");
    }

    @Test
    void skipsManualInvoiceAndUnknownProducts() {
        service.notify(transaction(PaymentProduct.CODE_MANUAL_INVOICE, "Иванов Иван", null, "buyer@mail.ru"));
        service.notify(transaction("SOMETHING_NEW", "Иванов Иван", null, "buyer@mail.ru"));
        service.notify(transaction(null, "Иванов Иван", null, "buyer@mail.ru"));

        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    @Test
    void usesFallbackContactNameAndSkipsFioWhenNameMissing() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead("Неуспешная оплата - GUIDE", "Клиент", null,
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID, IRINA_ID)).thenReturn(555L);

        service.notify(transaction(PaymentProduct.CODE_GUIDE, null, null, "buyer@mail.ru"));

        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.TASK_TEXT, 555L, ONE_DAY_MINUTES);
    }

    @Test
    void attachesLeadToExistingContactWithoutTouchingContactData() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.createLead("Неуспешная оплата - GUIDE", "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID, IRINA_ID)).thenReturn(555L);

        service.notify(transaction(PaymentProduct.CODE_GUIDE, "Иванов Иван", "+79001234567", "buyer@mail.ru"));

        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
        verify(amoCrmGateway).setNewTask(IRINA_ID, LOST_MESSAGE_TASK_TYPE_ID,
                FailedPaymentNotificationServiceImpl.TASK_TEXT, 555L, ONE_DAY_MINUTES);
    }

    @Test
    void doesNothingElseWhenAmoDisabled() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(null);

        service.notify(transaction(PaymentProduct.CODE_GUIDE, "Иванов Иван", null, "buyer@mail.ru"));

        verify(amoCrmGateway, never()).getContactIdFromLead(anyLong());
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    @Test
    void propagatesLeadCreationFailureWithoutTask() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("amo down"));

        assertThrows(RuntimeException.class, () -> service.notify(
                transaction(PaymentProduct.CODE_GUIDE, "Иванов Иван", null, "buyer@mail.ru")));

        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }
}
