package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.amo.GuideAmoLeadTaskPayload;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.payment.GuideAmoLeadService;

import java.util.List;

@Component
class GuideAmoLeadTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final GetterTransaction getterTransaction;
    private final GuideAmoLeadService guideAmoLeadService;
    private final Gson gson = new Gson();

    GuideAmoLeadTaskRunner(GetterTaskByStatus getterTaskByStatus,
                           GetterTransaction getterTransaction,
                           GuideAmoLeadService guideAmoLeadService,
                           SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.getterTransaction = getterTransaction;
        this.guideAmoLeadService = guideAmoLeadService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_GUIDE_LEAD, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        GuideAmoLeadTaskPayload payload = gson.fromJson(task.getPayload(), GuideAmoLeadTaskPayload.class);
        PaymentTransaction transaction = getterTransaction.getById(payload.getTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Транзакция не найдена: " + payload.getTransactionId()));
        guideAmoLeadService.pushGuidePurchase(transaction);
    }
}
