package ru.anyforms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderShipmentCheckerScheduler {
    private static final Logger logger = LoggerFactory.getLogger(OrderShipmentCheckerScheduler.class);
    
    private final OrderShipmentCheckerService orderShipmentCheckerService;

    public OrderShipmentCheckerScheduler(OrderShipmentCheckerService orderShipmentCheckerService) {
        this.orderShipmentCheckerService = orderShipmentCheckerService;
    }

    /**
     * Периодическая проверка всех заказов на отправку
     * Выполняется каждый час (3600000 миллисекунд)
     * Первый запуск через 1 минуту (60000 мс) после старта
     */
    @Scheduled(
            fixedRate = 3_600_000,      // раз в час
            initialDelay = 5_000       // первый запуск через 1 минуту
    )
    public void checkOrdersForShipment() {
        logger.info("Запуск периодической проверки заказов на отправку");
        orderShipmentCheckerService.checkAllOrdersForShipment();
    }

}


