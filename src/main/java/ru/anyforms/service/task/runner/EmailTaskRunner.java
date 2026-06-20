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
import ru.anyforms.service.email.PurchaseEmailRenderer;

import java.util.List;

@Slf4j
@Component
class EmailTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final PurchaseEmailRenderer purchaseEmailRenderer;
    private final EmailService emailService;
    private final Gson gson = new Gson();

    EmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                    PurchaseEmailRenderer purchaseEmailRenderer,
                    EmailService emailService,
                    SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.purchaseEmailRenderer = purchaseEmailRenderer;
        this.emailService = emailService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        EmailTaskPayload payload = gson.fromJson(task.getPayload(), EmailTaskPayload.class);
        PurchaseEmailRenderer.RenderedEmail email = purchaseEmailRenderer.render(payload.getProductCode());
        emailService.sendEmail(payload.getTo(), email.subject(), email.html());
    }
}
