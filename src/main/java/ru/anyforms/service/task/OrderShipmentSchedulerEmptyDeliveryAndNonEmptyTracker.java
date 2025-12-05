package ru.anyforms.service.task;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.AmoLeadStatus;
import ru.anyforms.model.CdekOrderStatus;
import ru.anyforms.model.Order;
import ru.anyforms.repository.GetterOrder;
import ru.anyforms.repository.SaverOrder;

import java.util.Set;

@Log4j2
@Component
@AllArgsConstructor
public class OrderShipmentSchedulerEmptyDeliveryAndNonEmptyTracker {

    private final GetterOrder getterOrder;
    private final AmoCrmGateway amoCrmGateway;
    private final SaverOrder saverOrder;

    public static final Set<String> READY_KEYWORDS = Set.of(
            "готов", "отправ", "забрал", "лично"
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
        var orders = getterOrder.getEmptyDeliveryAndNonEmptyTracker();
        for (var order : orders) {
            try {
                processOrder(order);
            } catch (Exception e) {
                log.error(e);
            }
        }
    }

    private void processOrder(Order order) {
        // process if relised
        var lead = amoCrmGateway.getLead(order.getLeadId());
        if (lead.getPipelineId() != null && lead.getPipelineId().equals(AmoLeadStatus.REALIZED.getStatusId())) {
            order.setDeliveryStatus(CdekOrderStatus.DELIVERED.getCode());
            saverOrder.save(order);
            log.info("order is delivered");
            return;
        }

        // process if ready -> move status amo
        if (isReady(order.getTracker())) {
            amoCrmGateway.updateLeadStatus(lead.getId(), AmoLeadStatus.DELIVERED);
            order.setDeliveryStatus(CdekOrderStatus.ACCEPTED_AT_PICK_UP_POINT.getCode());
            saverOrder.save(order);
            log.info("order could be pickuped");
        }
    }

    private boolean isReady(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        String normalized = status.toLowerCase();

        return READY_KEYWORDS.stream().anyMatch(normalized::contains);
    }

}


