package ru.anyforms.service.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.service.impl.OrderShipmentCheckerService;

@Component
public class OrderShipmentCheckerScheduler {
    private static final Logger logger = LoggerFactory.getLogger(OrderShipmentCheckerScheduler.class);
    
    private final OrderShipmentCheckerService orderShipmentCheckerService;
    private final OrderAdderScheduler orderAdderScheduler;

    public OrderShipmentCheckerScheduler(OrderShipmentCheckerService orderShipmentCheckerService, OrderAdderScheduler orderAdderScheduler) {
        this.orderShipmentCheckerService = orderShipmentCheckerService;
        this.orderAdderScheduler = orderAdderScheduler;
    }

    /**
     * Периодическая проверка всех заказов на отправку
     * Выполняется каждый час (3600000 миллисекунд)
     * Первый запуск через 1 минуту (60000 мс) после старта
     */
//    @Scheduled(
//            fixedRate = 3_600_000,      // раз в час
//            initialDelay = 5_000       // первый запуск через 1 минуту
//    )
    public void checkOrdersForShipment() {
        logger.info("Запуск периодической проверки заказов на отправку");
        orderShipmentCheckerService.checkAllOrdersForShipment();
        orderAdderScheduler.updateAllUntrackedOrders();
    }

}


