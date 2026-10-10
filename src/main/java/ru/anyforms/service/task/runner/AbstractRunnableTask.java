package ru.anyforms.service.task.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.repository.SaverTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class AbstractRunnableTask {

    private final SaverTask saverTask;
    private final AtomicBoolean running = new AtomicBoolean();

    @Value("${tasks.batch-size}")
    private int batchSize;

    protected AbstractRunnableTask(SaverTask saverTask) {
        this.saverTask = saverTask;
    }

    protected abstract List<Task> fetchBatch(int batchSize);

    protected abstract void process(Task task) throws Exception;

    protected Executor batchExecutor() {
        return Runnable::run;
    }

    protected int maxAttempts() {
        return 1;
    }

    protected boolean retryable(Exception ex) {
        return true;
    }

    protected Duration retryDelay(int attempt) {
        return Duration.ofMinutes(5L * attempt);
    }

    @Scheduled(fixedRateString = "${tasks.fixed-rate-ms}", initialDelayString = "${tasks.initial-delay-ms}")
    public void runBatch() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        boolean handedOff = false;
        try {
            batchExecutor().execute(this::runClaimedBatch);
            handedOff = true;
        } finally {
            if (!handedOff) {
                running.set(false);
            }
        }
    }

    private void runClaimedBatch() {
        try {
            for (Task t : fetchBatch(batchSize)) {
                runOne(t);
            }
        } catch (Exception ex) {
            log.error("Ошибка при выборке тасок в {}", getClass().getSimpleName(), ex);
        } finally {
            running.set(false);
        }
    }

    private void runOne(Task t) {
        try {
            t.setStatus(TaskStatus.RUNNING);
            saverTask.save(t);
            process(t);
        } catch (Exception ex) {
            log.error("Ошибка во время исполнения таски {}", t.getId(), ex);
            fail(t, ex);
            return;
        }
        try {
            t.setStatus(TaskStatus.DONE);
            saverTask.save(t);
        } catch (Exception ex) {
            log.error("Таска {} выполнена, но статус DONE не сохранён", t.getId(), ex);
        }
    }

    private void fail(Task t, Exception ex) {
        int attempt = t.getAttempts() + 1;
        t.setAttempts(attempt);
        t.setComment(crop(ex.getMessage()));
        if (retryable(ex) && attempt < maxAttempts()) {
            t.setStatus(TaskStatus.NEW);
            t.setNextAttemptAt(Instant.now().plus(retryDelay(attempt)));
            log.warn("Таска {} будет повторена: попытка {} из {}", t.getId(), attempt + 1, maxAttempts());
        } else {
            t.setStatus(TaskStatus.FAILED);
        }
        saverTask.save(t);
    }

    private static String crop(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
