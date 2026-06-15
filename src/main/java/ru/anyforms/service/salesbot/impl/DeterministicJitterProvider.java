package ru.anyforms.service.salesbot.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.service.salesbot.JitterProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Детерминированный джиттер: для пары (слот, день) сдвиг стабилен в течение суток,
 * но меняется день ото дня (seed = f(slotId, день эпохи)). Диапазон сдвига —
 * {@code [-maxMinutes, +maxMinutes]}, по умолчанию ±10 минут.
 * <p>
 * Детерминизм важен: тикер каждую минуту сравнивает «сейчас» с эффективным временем
 * слота; если бы джиттер был случаен на каждый тик, сравнение «дребезжало» бы.
 */
@Slf4j
@Component
class DeterministicJitterProvider implements JitterProvider {

    private static final long SECONDS_PER_DAY = 86_400L;

    private final int maxMinutes;

    DeterministicJitterProvider(@Value("${salesbot.jitter.max-minutes}") int maxMinutes) {
        this.maxMinutes = Math.max(0, maxMinutes);
    }

    @Override
    public Duration jitterFor(long slotId, Instant dayUtc) {
        if (maxMinutes == 0) {
            return Duration.ZERO;
        }
        long epochDay = Math.floorDiv(dayUtc.getEpochSecond(), SECONDS_PER_DAY);
        long seed = slotId * 1_000_003L + epochDay;
        // [-maxMinutes, +maxMinutes]
        int offset = new Random(seed).nextInt(2 * maxMinutes + 1) - maxMinutes;
        return Duration.ofMinutes(offset);
    }
}
