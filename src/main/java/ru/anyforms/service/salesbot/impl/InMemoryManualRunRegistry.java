package ru.anyforms.service.salesbot.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.anyforms.service.salesbot.ManualRun;
import ru.anyforms.service.salesbot.ManualRunRegistry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** Хранит последние {@link #KEEP} запусков в памяти процесса. */
@Component
class InMemoryManualRunRegistry implements ManualRunRegistry {

    static final int KEEP = 30;

    private final Clock clock;
    private final AtomicLong ids = new AtomicLong();
    /** Новые — в голове. */
    private final ConcurrentLinkedDeque<ManualRun> runs = new ConcurrentLinkedDeque<>();

    /** Конструктор для Spring: явно, т.к. в классе два конструктора (второй, с часами, — для тестов). */
    @Autowired
    InMemoryManualRunRegistry() {
        this(Clock.systemUTC());
    }

    InMemoryManualRunRegistry(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized ManualRun create(Long pipelineId, Long statusId, Long botId, String tagName, Boolean retail,
                                         String startedBy) {
        ManualRun run = new ManualRun(ids.incrementAndGet(), clock.instant(), pipelineId, statusId, botId,
                tagName, retail, startedBy);
        runs.addFirst(run);
        while (runs.size() > KEEP) {
            runs.pollLast();
        }
        return run;
    }

    @Override
    public List<ManualRun> recent() {
        return new ArrayList<>(runs);
    }

    @Override
    public Optional<ManualRun> running() {
        return runs.stream().filter(ManualRun::isRunning).findFirst();
    }
}
