package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.service.payment.CourseAmoLeadService;
import ru.anyforms.util.MoneyUtil;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class CourseAmoLeadServiceImpl implements CourseAmoLeadService {

    static final String LEAD_NAME = "Обучение - Курс куплен";
    static final String TAG = "Курс куплен";

    private final AmoCrmGateway amoCrmGateway;
    private final AmoContactFinder amoContactFinder;
    private final EducationLeadFinder educationLeadFinder;

    @Value("${amocrm.education.pipeline.id}")
    private Long educationPipelineId;

    @Override
    public void pushCoursePurchase(PaymentTransaction transaction) {
        Long contactId = amoContactFinder.findByEmailOrPhone(
                transaction.getEmail(), transaction.getContactPhone());
        Long existingLeadId = contactId == null ? null : educationLeadFinder.findLatestActiveLead(contactId);
        Long leadId = existingLeadId != null ? existingLeadId : createRealizedLead(transaction, contactId);
        if (leadId == null) {
            log.info("Курс: АМО выключена — сделка по транзакции {} не создана", transaction.getId());
            return;
        }

        Long priceRub = transaction.getAmount() == null ? null : transaction.getAmount() / 100;
        if (!amoCrmGateway.updateLeadFields(leadId, priceRub, Map.of())) {
            throw new IllegalStateException("Не удалось проставить бюджет сделки " + leadId);
        }
        if (!amoCrmGateway.addTagToLead(leadId, TAG)) {
            throw new IllegalStateException("Не удалось добавить тег «" + TAG + "» сделке " + leadId);
        }
        if (!amoCrmGateway.addNoteToLead(leadId, noteText(transaction))) {
            throw new IllegalStateException("Не удалось добавить примечание сделке " + leadId);
        }
        if (existingLeadId != null
                && !amoCrmGateway.updateLeadStatus(leadId, AmoLeadStatus.REALIZED.getStatusId(), educationPipelineId)) {
            throw new IllegalStateException("Не удалось перевести сделку " + leadId + " в «Реализовано»");
        }
        log.info("Курс: сделка {} закрыта в «Реализовано» с бюджетом {} ₽ и тегом «{}» (транзакция {})",
                leadId, priceRub, TAG, transaction.getId());
    }

    private Long createRealizedLead(PaymentTransaction transaction, Long contactId) {
        Long responsibleUserId = contactId == null ? null : contactResponsible(contactId);
        log.info("Курс: активной сделки в воронке {} нет (контакт {}, ответственный {}) — создаём новую (транзакция {})",
                educationPipelineId, contactId, responsibleUserId, transaction.getId());
        String name = transaction.getContactName() != null ? transaction.getContactName() : "Клиент";
        Long leadId = amoCrmGateway.createLead(LEAD_NAME, name, transaction.getContactPhone(),
                transaction.getEmail(), educationPipelineId, AmoLeadStatus.REALIZED.getStatusId(), responsibleUserId);
        if (leadId != null && contactId == null) {
            amoContactFinder.fillContactFio(leadId, transaction.getContactName());
            alignNewContactResponsible(leadId, transaction);
        }
        return leadId;
    }

    private Long contactResponsible(Long contactId) {
        AmoContact contact = amoCrmGateway.getContact(contactId);
        return contact == null ? null : contact.getResponsibleUserId();
    }

    private void alignNewContactResponsible(Long leadId, PaymentTransaction transaction) {
        AmoLead lead = amoCrmGateway.getLead(leadId);
        Long contactId = amoCrmGateway.getContactIdFromLead(leadId);
        if (lead == null || lead.getResponsibleUserId() == null || contactId == null) {
            log.warn("Курс: не удалось выровнять ответственного за новый контакт по сделке {} (транзакция {})",
                    leadId, transaction.getId());
            return;
        }
        if (!amoCrmGateway.updateContactResponsible(contactId, lead.getResponsibleUserId())) {
            log.warn("Курс: не удалось назначить ответственного {} за контакт {} (транзакция {})",
                    lead.getResponsibleUserId(), contactId, transaction.getId());
        }
    }

    private static String noteText(PaymentTransaction transaction) {
        StringBuilder note = new StringBuilder("Курс куплен");
        if (transaction.getAmount() != null) {
            note.append(" на сумму ").append(MoneyUtil.kopecksToString(transaction.getAmount())).append(" ₽");
        }
        note.append(" (").append(transaction.getProductCode())
                .append(", почта ").append(transaction.getEmail())
                .append(")");
        return note.toString();
    }
}
