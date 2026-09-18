package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.dto.amo.SalesbotRunTaskPayload;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.OrderService;
import ru.anyforms.service.task.TaskAdder;
import ru.anyforms.service.telegram.TelegramNotificationQueue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceFulfillmentServiceTest {

    private static final long LEAD_ID = 777L;

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final OrderService orderService = mock(OrderService.class);
    private final TaskAdder taskAdder = mock(TaskAdder.class);
    private final TelegramNotificationQueue telegramNotificationQueue = mock(TelegramNotificationQueue.class);
    private final MarketplaceFulfillmentService service = new MarketplaceFulfillmentService(
            orderRepository, amoCrmGateway, orderService, taskAdder, telegramNotificationQueue);

    private final Order order = new Order();
    private final PaymentTransaction transaction = PaymentTransaction.builder()
            .id(UUID.randomUUID())
            .orderId(1L)
            .email("buyer@mail.ru")
            .amount(150000L)
            .build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "retailPipelineId", 10L);
        ReflectionTestUtils.setField(service, "readyToShipStatusId", 20L);
        ReflectionTestUtils.setField(service, "productsCatalogId", 30L);
        order.setId(1L);
        order.setContactName("Иван");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong()))
                .thenReturn(LEAD_ID);
    }

    @Test
    void anyformsOrderCreatesLeadAndRunsThankYouBot() {
        order.setShop(shop(Shop.DEFAULT_SLUG));

        service.fulfill(transaction);

        assertEquals(LEAD_ID, order.getLeadId());
        List<SalesbotRunTaskPayload> bots = salesbotTasks();
        assertEquals(1, bots.size());
        assertEquals(LEAD_ID, bots.get(0).getLeadId());
    }

    @Test
    void orderWithoutShopRunsThankYouBot() {
        order.setShop(null);

        service.fulfill(transaction);

        assertEquals(1, salesbotTasks().size());
    }

    @Test
    void partnerShopOrderCreatesLeadButDoesNotRunBot() {
        order.setShop(shop("af_pastry"));

        service.fulfill(transaction);

        assertEquals(LEAD_ID, order.getLeadId());
        verify(amoCrmGateway).createLead(anyString(), eq("Иван"), any(), eq("buyer@mail.ru"), eq(10L), eq(20L));
        verify(orderService).syncOrder(LEAD_ID);
        assertEquals(1, tasks(MarketplaceOrderEmailPayload.class).size());
        assertTrue(salesbotTasks().isEmpty());
    }

    private List<SalesbotRunTaskPayload> salesbotTasks() {
        return tasks(SalesbotRunTaskPayload.class);
    }

    private <T> List<T> tasks(Class<T> type) {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(taskAdder, atLeastOnce()).addTask(payloads.capture());
        return payloads.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static Shop shop(String slug) {
        Shop shop = new Shop();
        shop.setSlug(slug);
        shop.setName(slug);
        return shop;
    }
}
