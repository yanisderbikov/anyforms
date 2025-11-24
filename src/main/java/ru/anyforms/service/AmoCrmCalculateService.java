package ru.anyforms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.model.AmoLead;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AmoCrmCalculateService {
    private final AmoCrmService amoCrmService;
    
    @Value("${amocrm.calculate.base.amount}")
    private Long baseAmount;
    
    // ID кастомных полей
    private static final Long FIELD_MIN_FORMS_COUNT = 2337779L; // Мин-кол-во форм
    private static final Long FIELD_FORMS_COUNT = 2351399L; // кол-во форм
    private static final Long FIELD_FORM_PRICE = 2337773L; // Форма
    private static final Long FIELD_PROJECT_PRICE = 2337775L; // проект

    /**
     * Выполняет расчеты для сделки и обновляет поля
     * @param leadId ID сделки
     * @return true если расчеты выполнены успешно, false в противном случае
     */
    public boolean calculateAndUpdateLead(Long leadId) {
        try {
            // Получаем сделку
            AmoLead lead = amoCrmService.getLead(leadId);
            if (lead == null) {
                System.err.println("Failed to get lead " + leadId);
                return false;
            }

            // Получаем значения полей
            String projectPriceStr = lead.getCustomFieldValue(FIELD_PROJECT_PRICE);
            String formPriceStr = lead.getCustomFieldValue(FIELD_FORM_PRICE);
            
            if (projectPriceStr == null || formPriceStr == null) {
                System.err.println("Required fields are missing for lead " + leadId);
                System.err.println("Project price: " + projectPriceStr + ", Form price: " + formPriceStr);
                return false;
            }

            // Парсим значения
            Long projectPrice;
            Long formPrice;
            try {
                projectPrice = Long.parseLong(projectPriceStr.trim());
                formPrice = Long.parseLong(formPriceStr.trim());
            } catch (NumberFormatException e) {
                System.err.println("Failed to parse numeric values for lead " + leadId);
                System.err.println("Project price: " + projectPriceStr + ", Form price: " + formPriceStr);
                return false;
            }


            // Проверяем, что форма не равна нулю (деление на ноль)
            if (formPrice == 0) {
                System.err.println("Form price cannot be zero for lead " + leadId);
                return false;
            }

            // Расчет 1: Минимальное кол-во форм = ceil((baseAmount - проект) / Форма)
            double minFormsCountDouble = (double)(baseAmount - projectPrice) / formPrice;
            long minFormsCount = (long) Math.ceil(minFormsCountDouble);
            
            // Убеждаемся, что результат не отрицательный
            if (minFormsCount < 0) {
                minFormsCount = 0;
            }

            // Расчет 2: Бюджет = проект + Форма * кол-во форм
            long formsCount = minFormsCount; // кол-во форм = Мин-кол-во форм
            long budget = projectPrice + formPrice * formsCount;

            // Подготавливаем данные для обновления
            Map<Long, String> customFields = new HashMap<>();
            customFields.put(FIELD_MIN_FORMS_COUNT, String.valueOf(minFormsCount));
            customFields.put(FIELD_FORMS_COUNT, String.valueOf(formsCount));

            // Обновляем поля в сделке
            boolean updated = amoCrmService.updateLeadFields(leadId, budget, customFields);
            
            if (updated) {
                System.out.println("Successfully calculated and updated lead " + leadId);
                System.out.println("Min forms count: " + minFormsCount);
                System.out.println("Forms count: " + formsCount);
                System.out.println("Budget: " + budget);
            }
            
            return updated;
        } catch (Exception e) {
            System.err.println("Error calculating and updating lead " + leadId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

