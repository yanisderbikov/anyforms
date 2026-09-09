package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.service.salesbot.RunWindowGate;

import java.time.Duration;
import java.time.Instant;

/**
 * Прогон не чаще раза в {@code salesbot.run.interval-minutes}. Время суток здесь не
 * ограничивается: у каждой группы своё окно отправки, проверяем круглосуточно. Память
 * «когда запускались» — в процессе; после рестарта первый тик запустит прогон, это безопасно:
 * задержки шагов, окна групп и {@code UNIQUE(lead_id, bot_id)} не дадут отправить лишнего.
 */
@Slf4j
@Component
class RunWindowGateImpl implements RunWindowGate {

    private final Duration interval;
    private volatile Instant lastRun;

    RunWindowGateImpl(@Value("${salesbot.run.interval-minutes}") int intervalMinutes) {
        if (intervalMinutes < 1) {
            throw new IllegalArgumentException("salesbot.run.interval-minutes должен быть >= 1, а не " + intervalMinutes);
        }
        this.interval = Duration.ofMinutes(intervalMinutes);
    }

    @Override
    public synchronized boolean tryClaim(Instant now) {
        if (lastRun != null && now.isBefore(lastRun.plus(interval))) {
            return false;
        }
        lastRun = now;
        log.info("Drip run is due (every {} min)", interval.toMinutes());
        return true;
    }
}
