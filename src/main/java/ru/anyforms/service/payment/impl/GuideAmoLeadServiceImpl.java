package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.model.amo.AmoTaskResponsibleUser;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.service.payment.GuideAmoLeadService;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class GuideAmoLeadServiceImpl implements GuideAmoLeadService {

    static final String LEAD_NAME = "Обучение - Гайд куплен";
    static final String TASK_TEXT = "Связаться - купили гайд";
    static final int TASK_DEADLINE_MINUTES = 3 * 24 * 60;

    private final AmoCrmGateway amoCrmGateway;
    private final AmoContactFinder amoContactFinder;
    private final EducationLeadFinder educationLeadFinder;

    @Value("${amocrm.education.pipeline.id}")
    private Long educationPipelineId;

    @Value("${amocrm.education.guide.bought.status.id}")
    private Long guideBoughtStatusId;

    @Override
    public void pushGuidePurchase(PaymentTransaction transaction) {
        Long existingContactId = amoContactFinder.findByEmailOrPhone(
                transaction.getEmail(), transaction.getContactPhone());
        Long leadId = existingContactId != null
                ? reuseOrCreateLeadForExistingContact(existingContactId, transaction)
                : createLeadWithNewContact(transaction);
        if (leadId == null) {
            log.info("Гайд: АМО выключена — сделка по транзакции {} не создана", transaction.getId());
            return;
        }
        amoCrmGateway.setNewTask(AmoTaskResponsibleUser.IRINA.getResponsibleUserId(),
                AmoTaskId.REACH_OUT.getTaskId(), TASK_TEXT, leadId, TASK_DEADLINE_MINUTES);
        log.info("Гайд: сделка {} и задача «{}» (транзакция {})",
                leadId, TASK_TEXT, transaction.getId());
    }

    private Long reuseOrCreateLeadForExistingContact(Long contactId, PaymentTransaction transaction) {
        Long existingLeadId = educationLeadFinder.findLatestActiveLead(contactId);
        if (existingLeadId != null) {
            return moveLeadToGuideBought(existingLeadId, transaction);
        }
        log.info("Гайд: клиент уже есть в АМО (контакт {}) — сделка будет привязана к нему (транзакция {})",
                contactId, transaction.getId());
        return createLead(transaction);
    }

    private Long moveLeadToGuideBought(Long leadId, PaymentTransaction transaction) {
        log.info("Гайд: у клиента уже есть активная сделка {} в воронке обучения — переводим в «Гайд куплен» (транзакция {})",
                leadId, transaction.getId());
        if (!amoCrmGateway.updateLeadStatus(leadId, guideBoughtStatusId, educationPipelineId)) {
            throw new IllegalStateException("Не удалось перевести сделку " + leadId + " в статус «Гайд куплен»");
        }
        return leadId;
    }

    private Long createLeadWithNewContact(PaymentTransaction transaction) {
        Long leadId = createLead(transaction);
        if (leadId != null) {
            fillContactFio(leadId, transaction);
        }
        return leadId;
    }

    private Long createLead(PaymentTransaction transaction) {
        String name = transaction.getContactName() != null ? transaction.getContactName() : "Клиент";
        return amoCrmGateway.createLead(LEAD_NAME, name, transaction.getContactPhone(),
                transaction.getEmail(), educationPipelineId, guideBoughtStatusId);
    }

    private void fillContactFio(Long leadId, PaymentTransaction transaction) {
        if (transaction.getContactName() == null) {
            return;
        }
        Long contactId = amoCrmGateway.getContactIdFromLead(leadId);
        if (contactId == null) {
            log.warn("Гайд: у сделки {} нет контакта — ФИО не заполнено", leadId);
            return;
        }
        amoCrmGateway.updateContactCustomField(contactId,
                Map.of(AmoCrmFieldId.FIO_CONTACT.getId(), transaction.getContactName()));
    }
}
