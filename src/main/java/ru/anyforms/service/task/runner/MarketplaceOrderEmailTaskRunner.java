package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;
import ru.anyforms.service.email.EmailTemplate;

import java.util.List;

/** Раннер писем-чеков заказа маркетплейса (таски {@link TaskType#MARKETPLACE_ORDER_EMAIL}). */
@Slf4j
@Component
class MarketplaceOrderEmailTaskRunner extends AbstractRunnableTask {

    private static final String SUBJECT = "Ваш заказ anyforms оформлен";

    private final GetterTaskByStatus getterTaskByStatus;
    private final EmailService emailService;
    private final Gson gson = new Gson();

    MarketplaceOrderEmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                                    EmailService emailService,
                                    SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.emailService = emailService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.MARKETPLACE_ORDER_EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        MarketplaceOrderEmailPayload payload = gson.fromJson(task.getPayload(), MarketplaceOrderEmailPayload.class);
        String html = EmailTemplate.getMarketplaceOrderEmail(payload);
        emailService.sendEmail(payload.getTo(), SUBJECT, html);
    }
}
