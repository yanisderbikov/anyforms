package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.amo.FailedPaymentAmoTaskPayload;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.payment.FailedPaymentNotificationService;

import java.util.List;

@Component
class FailedPaymentAmoTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final GetterTransaction getterTransaction;
    private final FailedPaymentNotificationService failedPaymentNotificationService;
    private final Gson gson = new Gson();

    FailedPaymentAmoTaskRunner(GetterTaskByStatus getterTaskByStatus,
                               GetterTransaction getterTransaction,
                               FailedPaymentNotificationService failedPaymentNotificationService,
                               SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.getterTransaction = getterTransaction;
        this.failedPaymentNotificationService = failedPaymentNotificationService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_FAILED_PAYMENT, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        FailedPaymentAmoTaskPayload payload = gson.fromJson(task.getPayload(), FailedPaymentAmoTaskPayload.class);
        PaymentTransaction transaction = getterTransaction.getById(payload.getTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Транзакция не найдена: " + payload.getTransactionId()));
        failedPaymentNotificationService.notify(transaction);
    }
}
