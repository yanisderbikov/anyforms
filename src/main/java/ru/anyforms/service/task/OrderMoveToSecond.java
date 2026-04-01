package ru.anyforms.service.task;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.repository.GetterOrder;
import ru.anyforms.repository.OrderDeleter;
import ru.anyforms.service.DeliveryProcessor;

@Slf4j
@Component
@AllArgsConstructor
public class OrderMoveToSecond {

    private final AmoCrmGateway amoCrmGateway;
    private final OrderDeleter deleter;

    /**
     * Периодическая проверка всех заказов на отправку
     * Выполняется каждый час (3600000 миллисекунд)
     * Первый запуск через 1 минуту (60000 мс) после старта
     */
    @Scheduled(
            cron = "0 53 10 ? * MON-FRI",
            zone = "Europe/Moscow"
    )
    public void process() {
        try {
            long now = System.currentTimeMillis() / 1000;
            long twoWeeksAgo = now - (14L * 24 * 60 * 60);

            var leads = amoCrmGateway.getLeadIdsOlderThanTwoWeeks(9846162L, 142L, twoWeeksAgo);
            amoCrmGateway.updateLeadStatus(leads, 82053234L, 9939750L);

            deleter.deleteByLeadId(leads);
        } catch (Exception e) {
            log.error("Шедулер переноса не сработал", e);
        }
    }
}


