package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.model.amo.AmoTaskResponsibleUser;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.service.payment.FailedPaymentNotificationService;

@Service
@RequiredArgsConstructor
@Slf4j
class FailedPaymentNotificationServiceImpl implements FailedPaymentNotificationService {

    static final String LEAD_NAME_PREFIX = "Неуспешная оплата - ";
    static final String TASK_TEXT = "не получилось оплатить продукт - связаться";
    static final int TASK_DEADLINE_MINUTES = 24 * 60;

    static final long EDUCATION_PIPELINE_ID = 10863606L;
    static final long EDUCATION_FAILED_STATUS_ID = 85480278L;
    static final long MARKETPLACE_FAILED_PIPELINE_ID = 10557858L;
    static final long MARKETPLACE_FAILED_STATUS_ID = 83286998L;

    private final AmoCrmGateway amoCrmGateway;
    private final AmoContactFinder amoContactFinder;

    @Override
    public void notify(PaymentTransaction transaction) {
        PipelineTarget target = resolveTarget(transaction.getProductCode());
        if (target == null) {
            log.info("Неуспешная оплата: продукт {} в АМО не ведём (транзакция {})",
                    transaction.getProductCode(), transaction.getId());
            return;
        }

        Long existingContactId = amoContactFinder.findByEmailOrPhone(
                transaction.getEmail(), transaction.getContactPhone());

        Long existingLeadId = findExistingFailedLead(existingContactId, target);
        if (existingLeadId != null) {
            if (amoCrmGateway.hasIncompleteTask(existingLeadId)) {
                log.info("Неуспешная оплата: по сделке {} уже есть невыполненная задача — ничего не создаём (транзакция {})",
                        existingLeadId, transaction.getId());
                return;
            }
            amoCrmGateway.setNewTask(AmoTaskResponsibleUser.IRINA.getResponsibleUserId(),
                    AmoTaskId.LOST_MESSAGE.getTaskId(), TASK_TEXT, existingLeadId, TASK_DEADLINE_MINUTES);
            log.info("Неуспешная оплата: сделка {} уже есть — добавили только задачу (транзакция {})",
                    existingLeadId, transaction.getId());
            return;
        }

        String contactName = transaction.getContactName() != null ? transaction.getContactName() : "Клиент";
        Long leadId = amoCrmGateway.createLead(LEAD_NAME_PREFIX + transaction.getProductCode(),
                contactName, transaction.getContactPhone(), transaction.getEmail(),
                target.pipelineId(), target.statusId(),
                AmoTaskResponsibleUser.IRINA.getResponsibleUserId());
        if (leadId == null) {
            log.info("Неуспешная оплата: АМО выключена — сделка по транзакции {} не создана",
                    transaction.getId());
            return;
        }
        if (existingContactId == null) {
            amoContactFinder.fillContactFio(leadId, transaction.getContactName());
        }

        amoCrmGateway.setNewTask(AmoTaskResponsibleUser.IRINA.getResponsibleUserId(),
                AmoTaskId.LOST_MESSAGE.getTaskId(), TASK_TEXT, leadId, TASK_DEADLINE_MINUTES);
    }

    private Long findExistingFailedLead(Long contactId, PipelineTarget target) {
        if (contactId == null) {
            return null;
        }
        for (Long leadId : amoCrmGateway.getLeadIdsByContact(contactId)) {
            AmoLead lead = amoCrmGateway.getLead(leadId);
            if (lead != null && target.pipelineId().equals(lead.getPipelineId())) {
                return leadId;
            }
        }
        return null;
    }

    private PipelineTarget resolveTarget(String productCode) {
        if (productCode == null) {
            return null;
        }
        return switch (productCode) {
            case PaymentProduct.CODE_MARKETPLACE_CART ->
                    new PipelineTarget(MARKETPLACE_FAILED_PIPELINE_ID, MARKETPLACE_FAILED_STATUS_ID);
            case PaymentProduct.CODE_GUIDE, PaymentProduct.CODE_COURSE, PaymentProduct.CODE_COURSE_PERSONAL ->
                    new PipelineTarget(EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID);
            default -> null;
        };
    }

    private record PipelineTarget(Long pipelineId, Long statusId) {
    }
}
