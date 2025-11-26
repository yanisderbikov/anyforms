package ru.anyforms.service;

import ru.anyforms.model.AmoCrmFieldId;
import ru.anyforms.model.AmoLead;
import org.springframework.stereotype.Service;

@Service
public class LeadValidationService {

    /**
     * Проверяет, соответствует ли лид условиям для обработки:
     * - розница должна быть true
     * - мультисписок должен содержать "Лошадка"
     */
    public boolean isValidLead(AmoLead lead) {
        if (lead == null) {
            return false;
        }

        // Check conditions: розница must be true and мультисписок must contain "Лошадка"
        Boolean isRetail = lead.getCustomFieldBoolean(AmoCrmFieldId.RETAIL.getId());

        return isRetail != null && isRetail;
    }
}

