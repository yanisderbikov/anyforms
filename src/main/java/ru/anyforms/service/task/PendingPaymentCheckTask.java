package ru.anyforms.service.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.service.payment.PendingPaymentCheckService;

/**
 * Тикер поиска брошенных корзин розницы: раз в {@code payment.pending-check.interval-ms}
 * (по замыслу — 5 минут) запускает {@link PendingPaymentCheckService}.
 */
@Slf4j
@Component
public class PendingPaymentCheckTask {

    private final PendingPaymentCheckService pendingPaymentCheckService;
    private final boolean enabled;

    public PendingPaymentCheckTask(PendingPaymentCheckService pendingPaymentCheckService,
                                   @Value("${payment.pending-check.enabled}") boolean enabled) {
        this.pendingPaymentCheckService = pendingPaymentCheckService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${payment.pending-check.interval-ms}", initialDelay = 60_000)
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            pendingPaymentCheckService.check();
        } catch (Exception e) {
            log.error("Проверка зависших платежей розницы не сработала", e);
        }
    }
}
