package ru.anyforms.service.task;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.model.Order;
import ru.anyforms.repository.GetterOrder;
import ru.anyforms.service.DeliveryProcessor;

import java.util.Set;

@Log4j2
@Component
@AllArgsConstructor
public class OrderShipmentSchedulerNonDeliveredOrders {

    private final GetterOrder getterOrder;
    private final DeliveryProcessor deliveryProcessor;

    private static final Set<String> READY_KEYWORDS = Set.of(
            "готов", "отправ", "забрал", "личн"
    );

    /**
     * Периодическая проверка всех заказов на отправку
     * Выполняется каждый час (3600000 миллисекунд)
     * Первый запуск через 1 минуту (60000 мс) после старта
     */
    @Scheduled(
            fixedRate = 3_600_000,      // раз в час
            initialDelay = 5_000       // первый запуск через 1 минуту
    )
    public void process() {
        var orders = getterOrder.getNonDeliveredOrders();
        for (var order : orders) {
            try {
                processOrder(order);
            } catch (Exception e) {
                log.error(e);
            }
        }
    }

    private void processOrder(Order order) {
        deliveryProcessor.updateStatus(order.getTracker());
    }
}


