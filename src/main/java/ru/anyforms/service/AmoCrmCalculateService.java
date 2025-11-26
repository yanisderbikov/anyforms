package ru.anyforms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.model.AmoCrmFieldId;
import ru.anyforms.model.AmoLead;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AmoCrmCalculateService {
    private final AmoCrmService amoCrmService;
    
    @Value("${amocrm.calculate.base.amount}")
    private Long baseAmount;

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
            String projectPriceStr = lead.getCustomFieldValue(AmoCrmFieldId.PROJECT_PRICE.getId());
            String formPriceStr = lead.getCustomFieldValue(AmoCrmFieldId.FORM_PRICE.getId());
            
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

            // Добавляем 20% к проекту и 10% к форме
            long projectPriceWithMargin = Math.round(projectPrice * 1.2); // +20%
            long formPriceWithMargin = Math.round(formPrice * 1.1); // +10%

            // Расчет 1: Минимальное кол-во форм = ceil((baseAmount - проект_с_наценкой) / Форма_с_наценкой)
            double minFormsCountDouble = (double)(baseAmount - projectPriceWithMargin) / formPriceWithMargin;
            long minFormsCount = (long) Math.ceil(minFormsCountDouble);
            
            // Убеждаемся, что результат не отрицательный
            if (minFormsCount < 0) {
                minFormsCount = 0;
            }

            // Расчет 2: Бюджет = проект_с_наценкой + Форма_с_наценкой * кол-во форм
            long formsCount = minFormsCount; // кол-во форм = Мин-кол-во форм
            long budget = projectPriceWithMargin + formPriceWithMargin * formsCount;

            // Подготавливаем данные для обновления всех полей
            Map<Long, String> customFields = new HashMap<>();
            customFields.put(AmoCrmFieldId.PROJECT_PRICE.getId(), String.valueOf(projectPriceWithMargin)); // Обновляем проект с наценкой
            customFields.put(AmoCrmFieldId.FORM_PRICE.getId(), String.valueOf(formPriceWithMargin)); // Обновляем форму с наценкой
            customFields.put(AmoCrmFieldId.MIN_FORMS_COUNT.getId(), String.valueOf(minFormsCount));
            customFields.put(AmoCrmFieldId.FORMS_COUNT.getId(), String.valueOf(formsCount));

            return amoCrmService.updateLeadFields(leadId, budget, customFields);
        } catch (Exception e) {
            System.err.println("Error calculating and updating lead " + leadId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

