package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.email.DeliveryStatusEmailPayload;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;
import ru.anyforms.service.email.EmailTemplate;

import java.util.List;

@Slf4j
@Component
class DeliveryStatusEmailTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final EmailService emailService;
    private final Gson gson = new Gson();

    @Value("${support.phone}")
    private String supportPhone;

    DeliveryStatusEmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                                  EmailService emailService,
                                  SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.emailService = emailService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.DELIVERY_STATUS_EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        DeliveryStatusEmailPayload payload = gson.fromJson(task.getPayload(), DeliveryStatusEmailPayload.class);
        if (payload.getNotification() == null) {
            throw new IllegalArgumentException("У таски " + task.getId() + " не задан тип уведомления о доставке");
        }
        String html = EmailTemplate.getDeliveryStatusEmail(payload, supportPhone);
        boolean partnerShop = payload.getShopSlug() != null
                && !payload.getShopSlug().isBlank()
                && !Shop.DEFAULT_SLUG.equals(payload.getShopSlug());
        String shopName = partnerShop && payload.getShopName() != null && !payload.getShopName().isBlank()
                ? payload.getShopName()
                : Shop.DEFAULT_SLUG;
        String fromName = partnerShop ? "Команда " + shopName : null;
        String subject = EmailTemplate.getDeliveryStatusSubject(payload.getNotification(), payload.getOrderPublicId());
        emailService.sendEmail(payload.getTo(), subject, html, fromName);
    }
}
