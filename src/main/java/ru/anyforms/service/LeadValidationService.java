package ru.anyforms.service;

import ru.anyforms.model.AmoLead;
import org.springframework.stereotype.Service;

@Service
public class LeadValidationService {
    // Field IDs from requirements
    private static final Long FIELD_RETAIL = 2454667L; // Розница (должно быть true)
    private static final Long FIELD_MULTISELECT = 2482683L; // Мультисписок (должен содержать "Лошадка")

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
        Boolean isRetail = lead.getCustomFieldBoolean(FIELD_RETAIL);

        return isRetail != null && isRetail;
    }
}

