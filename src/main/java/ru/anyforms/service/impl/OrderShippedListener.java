package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.anyforms.event.OrderShippedEvent;
import ru.anyforms.service.DeliveryProcessor;

@Log4j2
@Component
@RequiredArgsConstructor
class OrderShippedListener {

    private final DeliveryProcessor deliveryProcessor;

    @Async
    @TransactionalEventListener
    public void onOrderShipped(OrderShippedEvent event) {
        try {
            deliveryProcessor.updateStatus(event.tracker());
        } catch (Exception e) {
            log.error("Не удалось обновить статус доставки по трекеру {}: {}", event.tracker(), e.getMessage(), e);
        }
    }
}
