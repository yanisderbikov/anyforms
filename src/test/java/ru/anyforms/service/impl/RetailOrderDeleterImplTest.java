package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.model.Order;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.repository.TelegramNotificationRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetailOrderDeleterImplTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CustomProductItemRepository customItems = mock(CustomProductItemRepository.class);
    private final TelegramNotificationRepository telegram = mock(TelegramNotificationRepository.class);
    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final SaverTransaction saverTransaction = mock(SaverTransaction.class);
    private final RetailOrderDeleterImpl deleter = new RetailOrderDeleterImpl(
            orderRepository, customItems, telegram, getterTransaction, saverTransaction);

    private Order order(boolean retail) {
        Order order = new Order();
        order.setId(7L);
        order.setRetail(retail);
        return order;
    }

    @Test
    void deletesRetailOrderAndDetachesPayments() {
        Order order = order(true);
        PaymentTransaction tx = PaymentTransaction.builder().orderId(7L).build();
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(customItems.countByOrderId(7L)).thenReturn(0L);
        when(getterTransaction.getByOrderId(7L)).thenReturn(List.of(tx));

        deleter.delete(7L);

        assertNull(tx.getOrderId());
        verify(saverTransaction).save(tx);
        verify(telegram).deleteByOrderIdIn(List.of(7L));
        verify(orderRepository).delete(order);
    }

    @Test
    void refusesCustomOrder() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(false)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> deleter.delete(7L));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void refusesRetailOrderWithCustomItems() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order(true)));
        when(customItems.countByOrderId(7L)).thenReturn(2L);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> deleter.delete(7L));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void missingOrderIs404() {
        when(orderRepository.findById(7L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> deleter.delete(7L));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
}
