package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.model.salesbot.ScheduleSlot;
import ru.anyforms.repository.ScheduleSlotRepository;
import ru.anyforms.service.salesbot.JitterProvider;
import ru.anyforms.service.salesbot.ScheduleGate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация {@link ScheduleGate}.
 * <p>
 * Алгоритм на каждый тик (UTC): берём активные слоты текущего дня недели, к базовому
 * времени каждого прибавляем детерминированный джиттер и получаем эффективное время.
 * Семантика — <b>catch-up</b>: слот считается due на первом тике В МОМЕНТ или ПОСЛЕ
 * эффективного времени (а не только в точно совпадающую минуту). Это устойчиво к
 * пропуску конкретной минуты (старт/рестарт приложения, пауза, редкие тики) и к
 * отрицательному джиттеру, сдвигающему время раньше базового. За сутки слот срабатывает
 * один раз — благодаря отметке «отработал сегодня».
 * <p>
 * Все вычисления — на {@link Instant}: «время суток» слота получаем как длительность от
 * UTC-полуночи его базового {@code time_utc}, прибавляем к UTC-полуночи текущих суток.
 * <p>
 * Память «слот уже отработал сегодня» — in-memory ({@link #firedOnDay}, ключ — UTC-полночь).
 * Повторные тики в течение суток отсекаются ею. От двойной отправки страхуют single-flight
 * лок и {@code UNIQUE(lead_id, bot_id)} в логе. Внимание: при catch-up память теряется на
 * рестарте, поэтому рестарт ПОЗЖЕ эффективного времени в тот же день может запустить прогон
 * повторно (лид продвинется на лишний шаг за сутки) — повторной отправки ОДНОГО И ТОГО ЖЕ
 * бота при этом не будет (идемпотентность лога). Если нужна строгая гарантия «один шаг в
 * сутки» через рестарты — отметку запуска слота надо персистить в БД.
 */
@Slf4j
@Component
@AllArgsConstructor
class ScheduleGateImpl implements ScheduleGate {

    private final ScheduleSlotRepository scheduleSlotRepository;
    private final JitterProvider jitterProvider;

    /** slotId -> UTC-полночь суток последнего запуска. */
    private final Map<Long, Instant> firedOnDay = new ConcurrentHashMap<>();

    @Override
    public boolean tryClaimDueSlot(Instant now) {
        Instant nowMinute = now.truncatedTo(ChronoUnit.MINUTES);
        Instant todayMidnightUtc = now.truncatedTo(ChronoUnit.DAYS);
        int weekday = now.atOffset(ZoneOffset.UTC).getDayOfWeek().getValue(); // ISO: 1=Пн ... 7=Вс

        List<ScheduleSlot> slots = scheduleSlotRepository.findByWeekdayAndEnabledIsTrue(weekday);
        for (ScheduleSlot slot : slots) {
            Duration timeOfDay = timeOfDayUtc(slot.getTimeUtc());
            Instant effective = todayMidnightUtc
                    .plus(timeOfDay)
                    .plus(jitterProvider.jitterFor(slot.getId(), todayMidnightUtc))
                    .truncatedTo(ChronoUnit.MINUTES);

            // Catch-up: срабатываем на первом тике В МОМЕНТ или ПОСЛЕ эффективного времени
            // (не требуем точного совпадения минуты). За сутки — один раз (claim).
            if (!nowMinute.isBefore(effective) && claim(slot.getId(), todayMidnightUtc)) {
                log.info("Schedule slot {} is due (effective={} UTC, now={}, weekday={})",
                        slot.getId(), effective, nowMinute, weekday);
                return true;
            }
        }
        return false;
    }

    /** UTC-составляющая времени суток базового {@code time_utc} как длительность от полуночи. */
    private static Duration timeOfDayUtc(Instant slotTime) {
        return Duration.between(slotTime.truncatedTo(ChronoUnit.DAYS), slotTime);
    }

    /** Атомарно помечает слот отработавшим сегодня; возвращает true, если это первый запуск за день. */
    private boolean claim(Long slotId, Instant today) {
        boolean[] claimed = {false};
        firedOnDay.compute(slotId, (id, previous) -> {
            if (today.equals(previous)) {
                return previous; // уже отрабатывал сегодня
            }
            claimed[0] = true;
            return today;
        });
        return claimed[0];
    }
}
