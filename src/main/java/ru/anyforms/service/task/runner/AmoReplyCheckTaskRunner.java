package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.amo.AmoReplyCheckTaskPayload;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.amo.MissedReplyChecker;

import java.time.Instant;
import java.util.List;

@Component
class AmoReplyCheckTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final MissedReplyChecker missedReplyChecker;
    private final int timeoutMinutes;
    private final Gson gson = new Gson();

    AmoReplyCheckTaskRunner(GetterTaskByStatus getterTaskByStatus,
                            MissedReplyChecker missedReplyChecker,
                            SaverTask saverTask,
                            @Value("${amocrm.reply.timeout-minutes}") int timeoutMinutes) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.missedReplyChecker = missedReplyChecker;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        Instant dueBefore = Instant.now().minusSeconds(timeoutMinutes * 60L);
        return getterTaskByStatus.getByTaskTypeAndStatusCreatedBefore(TaskType.AMO_REPLY_CHECK, TaskStatus.NEW, dueBefore, batchSize);
    }

    @Override
    protected void process(Task task) {
        missedReplyChecker.check(gson.fromJson(task.getPayload(), AmoReplyCheckTaskPayload.class));
    }
}
