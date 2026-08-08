package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.amo.CourseAmoLeadTaskPayload;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.payment.CourseAmoLeadService;

import java.util.List;

@Component
class CourseAmoLeadTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final GetterTransaction getterTransaction;
    private final CourseAmoLeadService courseAmoLeadService;
    private final Gson gson = new Gson();

    CourseAmoLeadTaskRunner(GetterTaskByStatus getterTaskByStatus,
                            GetterTransaction getterTransaction,
                            CourseAmoLeadService courseAmoLeadService,
                            SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.getterTransaction = getterTransaction;
        this.courseAmoLeadService = courseAmoLeadService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_COURSE_BOUGHT, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        CourseAmoLeadTaskPayload payload = gson.fromJson(task.getPayload(), CourseAmoLeadTaskPayload.class);
        PaymentTransaction transaction = getterTransaction.getById(payload.getTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Транзакция не найдена: " + payload.getTransactionId()));
        courseAmoLeadService.pushCoursePurchase(transaction);
    }
}
