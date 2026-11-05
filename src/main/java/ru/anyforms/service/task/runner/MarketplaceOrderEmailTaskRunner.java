package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.dto.promo.CoursePromoDTO;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;
import ru.anyforms.service.email.EmailTemplate;
import ru.anyforms.service.promo.CoursePromoService;

import java.util.List;

/**
 * Раннер писем-чеков заказа маркетплейса (таски {@link TaskType#MARKETPLACE_ORDER_EMAIL}).
 * Тема, отправитель и шаблон зависят от магазина заказа: у партнёрского магазина
 * письмо уходит под его брендом («Команда af_pastry»), у anyforms — как раньше.
 */
@Slf4j
@Component
class MarketplaceOrderEmailTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final EmailService emailService;
    private final CoursePromoService coursePromoService;
    private final Gson gson = new Gson();

    MarketplaceOrderEmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                                    EmailService emailService,
                                    CoursePromoService coursePromoService,
                                    SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.emailService = emailService;
        this.coursePromoService = coursePromoService;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.MARKETPLACE_ORDER_EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) {
        MarketplaceOrderEmailPayload payload = gson.fromJson(task.getPayload(), MarketplaceOrderEmailPayload.class);
        String html = EmailTemplate.getMarketplaceOrderEmail(payload, courseOffer(payload.getOrderPublicId()));
        boolean partnerShop = payload.getShopSlug() != null
                && !payload.getShopSlug().isBlank()
                && !Shop.DEFAULT_SLUG.equals(payload.getShopSlug());
        String shopName = partnerShop && payload.getShopName() != null && !payload.getShopName().isBlank()
                ? payload.getShopName()
                : Shop.DEFAULT_SLUG;
        String fromName = partnerShop ? "Команда " + shopName : null;
        emailService.sendEmail(payload.getTo(), "Ваш заказ " + shopName + " оформлен", html, fromName);
    }

    private CoursePromoDTO courseOffer(String orderPublicId) {
        try {
            return coursePromoService.issueForOrder(orderPublicId).promo();
        } catch (RuntimeException e) {
            log.error("Письмо по заказу {} уходит без скидки на курс: код не выдан", orderPublicId, e);
            return null;
        }
    }
}
