package ru.anyforms.service.salesbot;

import lombok.Getter;
import ru.anyforms.model.amo.LeadFilter;
import ru.anyforms.model.salesbot.ManualRunStatus;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Один ручной массовый запуск бота: параметры + живые счётчики, которые фоновая задача
 * обновляет по ходу. Живёт в памяти ({@link ManualRunRegistry}), рестарт приложения его теряет —
 * итоги при этом остаются в {@code bot_execution_log} (тип {@code MANUAL}).
 */
@Getter
public final class ManualRun {

    private final long id;
    private final Instant startedAt;
    private final Long pipelineId;
    private final Long statusId;
    private final Long botId;
    /** Тег-фильтр; {@code null} — все лиды статуса. */
    private final String tagName;
    /** Фильтр по полю «Розница»: {@code true} / {@code false} / {@code null} — любые. */
    private final Boolean retail;
    private final String startedBy;

    private volatile ManualRunStatus status = ManualRunStatus.RUNNING;
    private volatile Instant finishedAt;
    private volatile String error;
    /** Сколько лидов найдено в статусе; {@code -1} — ещё не посчитано. */
    private volatile int total = -1;
    private final AtomicInteger sent = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    public ManualRun(long id, Instant startedAt, Long pipelineId, Long statusId, Long botId,
                     String tagName, Boolean retail, String startedBy) {
        this.id = id;
        this.startedAt = startedAt;
        this.pipelineId = pipelineId;
        this.statusId = statusId;
        this.botId = botId;
        this.tagName = tagName;
        this.retail = retail;
        this.startedBy = startedBy;
    }

    /** Отбор лидов внутри статуса: тег + признак «Розница». */
    public LeadFilter leadFilter() {
        return LeadFilter.forManualRun(tagName, retail);
    }

    public void markTotal(int total) {
        this.total = total;
    }

    public void incSent() {
        sent.incrementAndGet();
    }

    public void incSkipped() {
        skipped.incrementAndGet();
    }

    public void incFailed() {
        failed.incrementAndGet();
    }

    public int getSent() {
        return sent.get();
    }

    public int getSkipped() {
        return skipped.get();
    }

    public int getFailed() {
        return failed.get();
    }

    public void finish(Instant at) {
        this.finishedAt = at;
        this.status = ManualRunStatus.DONE;
    }

    public void fail(Instant at, String error) {
        this.finishedAt = at;
        this.error = error;
        this.status = ManualRunStatus.FAILED;
    }

    public boolean isRunning() {
        return status == ManualRunStatus.RUNNING;
    }
}
