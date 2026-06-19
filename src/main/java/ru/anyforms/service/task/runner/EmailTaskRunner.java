package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.email.EmailTaskPayload;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;

import java.util.List;

/**
 * Раннер тасок отправки писем (по аналогии с FeedbackTaskRunner из vizhuonline).
 * Раз в интервал берёт новые EMAIL-таски и шлёт письмо через {@link EmailService}.
 */
@Slf4j
@Component
class EmailTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final EmailService emailService;
    private final Gson gson = new Gson();

    EmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                    EmailService emailService,
                    SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.emailService = emailService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        EmailTaskPayload payload = gson.fromJson(task.getPayload(), EmailTaskPayload.class);
        emailService.sendEmail(payload.getTo(), payload.getSubject(), payload.getHtml());
    }
}
