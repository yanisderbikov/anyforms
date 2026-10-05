package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.email.PromoPopupCodeEmailPayload;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;
import ru.anyforms.service.email.EmailTemplate;
import ru.anyforms.service.promo.PromoDiscountFormatter;

import java.util.List;

@Component
class PromoPopupCodeEmailTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final GetterPromoPopupLead getterPromoPopupLead;
    private final GetterPromoCode getterPromoCode;
    private final EmailService emailService;
    private final Gson gson = new Gson();

    PromoPopupCodeEmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                                  GetterPromoPopupLead getterPromoPopupLead,
                                  GetterPromoCode getterPromoCode,
                                  EmailService emailService,
                                  SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.getterPromoPopupLead = getterPromoPopupLead;
        this.getterPromoCode = getterPromoCode;
        this.emailService = emailService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.PROMO_POPUP_CODE_EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        PromoPopupCodeEmailPayload payload = gson.fromJson(task.getPayload(), PromoPopupCodeEmailPayload.class);
        PromoPopupLead lead = getterPromoPopupLead.getById(payload.getPopupLeadId())
                .orElseThrow(() -> new IllegalStateException("Заявка попапа не найдена: " + payload.getPopupLeadId()));
        PromoCode promo = getterPromoCode.getById(lead.getPromoCodeId())
                .orElseThrow(() -> new IllegalStateException("Промокод заявки не найден: " + lead.getPromoCodeId()));
        String discount = PromoDiscountFormatter.discount(promo.getDiscountPercent(), promo.getDiscountAmountKopecks());
        String html = EmailTemplate.getPromoCodeEmail(promo.getCode(), discount,
                PromoDiscountFormatter.lastValidDay(promo.getValidUntil()),
                promo.getMinOrderKopecks(), promo.isFirstOrderOnly());
        emailService.sendEmail(lead.getEmail(), EmailTemplate.getPromoCodeSubject(discount), html);
    }
}
