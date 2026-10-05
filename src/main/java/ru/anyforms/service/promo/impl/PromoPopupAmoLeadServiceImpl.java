package ru.anyforms.service.promo.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.SaverPromoPopupLead;
import ru.anyforms.service.promo.PromoDiscountFormatter;
import ru.anyforms.service.promo.PromoPopupAmoLeadService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
@Slf4j
class PromoPopupAmoLeadServiceImpl implements PromoPopupAmoLeadService {

    static final String LEAD_NAME_PREFIX = "Попап: ";
    static final String CONTACT_NAME = "Клиент";
    static final String TAG = "попап-промокод";

    private final AmoCrmGateway amoCrmGateway;
    private final GetterPromoPopup getterPromoPopup;
    private final GetterPromoCode getterPromoCode;
    private final SaverPromoPopupLead saverPromoPopupLead;

    @Value("${amocrm.promo-popup.pipeline.id}")
    private Long pipelineId;

    @Value("${amocrm.promo-popup.status.id}")
    private Long statusId;

    @Override
    public void pushLead(PromoPopupLead lead) {
        if (lead.getAmoLeadId() != null) {
            log.info("Попап: по заявке {} сделка {} уже создана", lead.getId(), lead.getAmoLeadId());
            return;
        }
        PromoPopup popup = getterPromoPopup.getById(lead.getPopupId())
                .orElseThrow(() -> new IllegalStateException("Попап не найден: " + lead.getPopupId()));

        Long leadId = amoCrmGateway.createLead(LEAD_NAME_PREFIX + popup.getName(), CONTACT_NAME,
                lead.getPhone(), lead.getEmail(), pipelineId, statusId, popup.getAmoResponsibleUserId(),
                utm(lead));
        if (leadId == null) {
            log.info("Попап: АМО выключена — сделка по заявке {} не создана", lead.getId());
            return;
        }
        lead.setAmoLeadId(leadId);
        saverPromoPopupLead.save(lead);

        try {
            amoCrmGateway.addTagToLead(leadId, TAG);
            amoCrmGateway.addNoteToLead(leadId, note(popup, lead));
        } catch (Exception e) {
            log.error("Попап: не удалось добавить тег или примечание к сделке {}", leadId, e);
        }
        amoCrmGateway.setNewTask(popup.getAmoResponsibleUserId(), popup.getAmoTaskTypeId(),
                taskText(popup, lead), leadId, popup.getAmoTaskDeadlineMinutes());
        log.info("Попап: сделка {} и задача по заявке {}", leadId, lead.getId());
    }

    private String taskText(PromoPopup popup, PromoPopupLead lead) {
        return "Попап «" + popup.getName() + "»: выдан промокод " + lead.getCode()
                + " (скидка " + discount(popup) + "). Связаться и помочь с первым заказом";
    }

    private String note(PromoPopup popup, PromoPopupLead lead) {
        StringJoiner note = new StringJoiner("\n");
        note.add("Заявка из попапа «" + popup.getName() + "» на витрине " + lead.getShopSlug());
        note.add("Промокод: " + lead.getCode() + ", скидка " + discount(popup));
        String lastDay = lead.getPromoCodeId() == null ? null : getterPromoCode.getById(lead.getPromoCodeId())
                .map(promo -> PromoDiscountFormatter.lastValidDay(promo.getValidUntil()))
                .orElse(null);
        if (lastDay != null) {
            note.add("Код действует по " + lastDay + " включительно");
        }
        note.add("Телефон: " + lead.getPhone());
        note.add("Почта: " + lead.getEmail());
        note.add("Согласие на обработку персональных данных: да");
        note.add("Согласие на получение рекламы: да");
        note.add("Версия текста согласий: " + lead.getConsentVersion());
        if (lead.getCreatedAt() != null) {
            note.add("Время согласия: " + lead.getCreatedAt());
        }
        if (lead.getIp() != null) {
            note.add("IP: " + lead.getIp());
        }
        if (lead.getPageUrl() != null) {
            note.add("Страница: " + lead.getPageUrl());
        }
        return note.toString();
    }

    private static String discount(PromoPopup popup) {
        return PromoDiscountFormatter.discount(popup.getDiscountPercent(), popup.getDiscountAmountKopecks());
    }

    private static Map<String, String> utm(PromoPopupLead lead) {
        Map<String, String> utm = new LinkedHashMap<>();
        put(utm, "UTM_SOURCE", lead.getUtmSource());
        put(utm, "UTM_MEDIUM", lead.getUtmMedium());
        put(utm, "UTM_CAMPAIGN", lead.getUtmCampaign());
        put(utm, "UTM_CONTENT", lead.getUtmContent());
        put(utm, "UTM_TERM", lead.getUtmTerm());
        return utm;
    }

    private static void put(Map<String, String> target, String fieldCode, String value) {
        if (value != null && !value.isBlank()) {
            target.put(fieldCode, value);
        }
    }
}
