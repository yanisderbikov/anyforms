package ru.anyforms.service.amo.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.LandingLeadRequestDTO;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.model.amo.AmoTaskResponsibleUser;
import ru.anyforms.service.amo.LandingLeadService;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
class LandingLeadServiceImpl implements LandingLeadService {
    /** amoCRM ограничивает длину текстовых полей; обрезаем UTM, чтобы запрос не отклонялся. */
    private static final int MAX_UTM_LENGTH = 255;

    private final AmoCrmGateway amoCrmGateway;

    @Override
    public Long createLead(LandingLeadRequestDTO request) {
        Map<String, String> utmByFieldCode = new LinkedHashMap<>();
        putUtm(utmByFieldCode, "UTM_SOURCE", request.getUtmSource());
        putUtm(utmByFieldCode, "UTM_MEDIUM", request.getUtmMedium());
        putUtm(utmByFieldCode, "UTM_CAMPAIGN", request.getUtmCampaign());
        putUtm(utmByFieldCode, "UTM_TERM", request.getUtmTerm());
        putUtm(utmByFieldCode, "UTM_CONTENT", request.getUtmContent());
        putUtm(utmByFieldCode, "UTM_REFERRER", request.getUtmReferrer());

        Long leadId = amoCrmGateway.createLandingLead(
                request.getLeadName(),
                request.getName(),
                request.getPhone(),
                request.getPipelineId(),
                request.getStatusId(),
                utmByFieldCode);

        // Сделка уже создана — ошибка постановки задачи не должна ронять запрос в 400
        try {
            amoCrmGateway.setNewTask(
                    AmoTaskResponsibleUser.IAN.getResponsibleUserId(),
                    AmoTaskId.LOST_MESSAGE.getTaskId(),
                    request.getLeadName(),
                    leadId,
                    10
            );
        } catch (Exception e) {
            log.error("Не удалось создать задачу по заявке с лендинга для сделки {}", leadId, e);
        }

        return leadId;
    }

    private void putUtm(Map<String, String> target, String fieldCode, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        target.put(fieldCode, trimmed.length() > MAX_UTM_LENGTH ? trimmed.substring(0, MAX_UTM_LENGTH) : trimmed);
    }
}
