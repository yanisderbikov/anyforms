package ru.anyforms.service.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.service.salesbot.DripCampaignRunner;
import ru.anyforms.service.salesbot.ScheduleGate;
import ru.anyforms.service.salesbot.SingleFlightLock;

import java.time.Instant;

/**
 * Тикер дрип-кампании. Раз в минуту спрашивает {@link ScheduleGate}, попадает ли текущая
 * минута в активный слот расписания, который сегодня ещё не отрабатывал. Если да — берёт
 * single-flight лок и запускает один прогон {@link DripCampaignRunner}.
 * <p>
 * Тикер намеренно «тонкий»: не знает деталей расписания, amoCRM и БД — только три порта.
 * Двухуровневая защита от двойной отправки: (1) {@link SingleFlightLock} — один прогон
 * одновременно; (2) {@code UNIQUE(lead_id, bot_id)} в логе.
 */
@Slf4j
@Component
public class SalesbotDripSchedulerTask {

    private final ScheduleGate scheduleGate;
    private final SingleFlightLock singleFlightLock;
    private final DripCampaignRunner dripCampaignRunner;
    private final boolean enabled;

    public SalesbotDripSchedulerTask(ScheduleGate scheduleGate,
                                     SingleFlightLock singleFlightLock,
                                     DripCampaignRunner dripCampaignRunner,
                                     @Value("${salesbot.scheduler.enabled}") boolean enabled) {
        this.scheduleGate = scheduleGate;
        this.singleFlightLock = singleFlightLock;
        this.dripCampaignRunner = dripCampaignRunner;
        this.enabled = enabled;
    }

    /**
     * Тик раз в минуту (интервал настраивается через {@code salesbot.scheduler.tick-ms}).
     */
    @Scheduled(fixedDelayString = "${salesbot.scheduler.tick-ms}", initialDelay = 60_000)
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            if (!scheduleGate.tryClaimDueSlot(Instant.now())) {
                return; // текущая минута не попадает в слот / слот уже отработал сегодня
            }
            boolean ran = singleFlightLock.runExclusively(dripCampaignRunner::runOnce);
            if (!ran) {
                log.info("Drip run was due but skipped: single-flight lock is held elsewhere");
            }
        } catch (Exception e) {
            log.error("Salesbot drip tick failed", e);
        }
    }
}
