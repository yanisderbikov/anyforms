package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.amo.PromoPopupAmoLeadTaskPayload;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.promo.PromoPopupAmoLeadService;

import java.util.List;

@Component
class PromoPopupAmoLeadTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final GetterPromoPopupLead getterPromoPopupLead;
    private final PromoPopupAmoLeadService promoPopupAmoLeadService;
    private final Gson gson = new Gson();

    PromoPopupAmoLeadTaskRunner(GetterTaskByStatus getterTaskByStatus,
                                GetterPromoPopupLead getterPromoPopupLead,
                                PromoPopupAmoLeadService promoPopupAmoLeadService,
                                SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.getterPromoPopupLead = getterPromoPopupLead;
        this.promoPopupAmoLeadService = promoPopupAmoLeadService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_PROMO_POPUP_LEAD, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        PromoPopupAmoLeadTaskPayload payload = gson.fromJson(task.getPayload(), PromoPopupAmoLeadTaskPayload.class);
        PromoPopupLead lead = getterPromoPopupLead.getById(payload.getPopupLeadId())
                .orElseThrow(() -> new IllegalStateException("Заявка попапа не найдена: " + payload.getPopupLeadId()));
        promoPopupAmoLeadService.pushLead(lead);
    }
}
