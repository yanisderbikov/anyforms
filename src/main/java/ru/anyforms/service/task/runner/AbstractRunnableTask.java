package ru.anyforms.service.task.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.repository.SaverTask;

import java.util.List;

@Slf4j
public abstract class AbstractRunnableTask {

    private final SaverTask saverTask;

    @Value("${tasks.batch-size}")
    private int batchSize;

    protected AbstractRunnableTask(SaverTask saverTask) {
        this.saverTask = saverTask;
    }

    protected abstract List<Task> fetchBatch(int batchSize);

    protected abstract void process(Task task) throws Exception;

    @Scheduled(fixedRateString = "${tasks.fixed-rate-ms}", initialDelayString = "${tasks.initial-delay-ms}")
    public void runBatch() {
        List<Task> tasks = fetchBatch(batchSize);
        for (Task t : tasks) {
            runOne(t);
        }
    }

    private void runOne(Task t) {
        try {
            t.setStatus(TaskStatus.RUNNING);
            saverTask.save(t);

            process(t);

            t.setStatus(TaskStatus.DONE);
            saverTask.save(t);
        } catch (Exception ex) {
            log.error("Ошибка во время исполнения таски {}", t.getId(), ex);
            t.setStatus(TaskStatus.FAILED);
            t.setComment(crop(ex.getMessage()));
            saverTask.save(t);
        }
    }

    private static String crop(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
