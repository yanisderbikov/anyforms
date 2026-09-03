package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.amo.SalesbotRunTaskPayload;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.salesbot.SalesbotTrigger;

import java.util.List;

@Component
class SalesbotRunTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final SalesbotTrigger salesbotTrigger;
    private final Gson gson = new Gson();

    SalesbotRunTaskRunner(GetterTaskByStatus getterTaskByStatus,
                          SalesbotTrigger salesbotTrigger,
                          SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.salesbotTrigger = salesbotTrigger;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_SALESBOT_RUN, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        SalesbotRunTaskPayload payload = gson.fromJson(task.getPayload(), SalesbotRunTaskPayload.class);
        if (payload.getLeadId() == null || payload.getBotId() == null) {
            throw new IllegalStateException("В таске нет leadId или botId: " + task.getPayload());
        }
        if (!salesbotTrigger.run(payload.getLeadId(), payload.getBotId())) {
            throw new IllegalStateException(
                    "Не удалось запустить бота " + payload.getBotId() + " для сделки " + payload.getLeadId());
        }
    }
}
