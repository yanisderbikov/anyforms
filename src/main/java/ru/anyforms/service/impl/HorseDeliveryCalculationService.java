package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.AmoContact;
import ru.anyforms.model.AmoCrmFieldId;
import ru.anyforms.model.AmoLead;

@Service
@RequiredArgsConstructor
@Deprecated
public class HorseDeliveryCalculationService {
    private static final Logger logger = LoggerFactory.getLogger(HorseDeliveryCalculationService.class);
    
    private final AmoCrmGateway amoCrmService;
    private final CdekDeliveryCalculatorService cdekDeliveryCalculatorService;
    
    // Значение для "Лошадка"
    private static final String PRODUCT_HORSE = "Лошадка";
    
    // Параметры для 1 лошадки
    private static final int HORSE_1_WEIGHT_GRAMS = 300; // 0.3 кг = 300 грамм
    private static final int HORSE_1_LENGTH = 15;
    private static final int HORSE_1_WIDTH = 14;
    private static final int HORSE_1_HEIGHT = 7;
    
    // Параметры для 3 лошадок
    private static final int HORSE_3_WEIGHT_GRAMS = 1200; // 1.2 кг = 1200 грамм
    private static final int HORSE_3_LENGTH = 28;
    private static final int HORSE_3_WIDTH = 26;
    private static final int HORSE_3_HEIGHT = 10;
    
    /**
     * Обрабатывает расчет доставки для сделки с лошадками
     * @param leadId ID сделки
     * @return true если расчет выполнен успешно, false в противном случае
     */
    public boolean calculateAndAddNote(Long leadId) {
        try {
            logger.info("Начало расчета доставки для сделки {}", leadId);
            
            // Получаем сделку
            AmoLead lead = amoCrmService.getLead(leadId);
            if (lead == null) {
                logger.error("Не удалось получить сделку {}", leadId);
                return false;
            }
            
            // Проверяем, что это объект "Лошадка"
            String productType = lead.getCustomFieldValue(AmoCrmFieldId.PRODUCT_TYPE.getId());
            if (productType == null || !productType.contains(PRODUCT_HORSE)) {
                logger.info("Сделка {} не является объектом 'Лошадка' (тип: {})", leadId, productType);
                return false;
            }
            
            // Получаем количество лошадок
            String horseCountStr = lead.getCustomFieldValue(AmoCrmFieldId.HORSE_COUNT.getId());
            if (horseCountStr == null || horseCountStr.trim().isEmpty()) {
                logger.error("Не указано количество лошадок для сделки {}", leadId);
                return false;
            }
            
            int horseCount;
            try {
                horseCount = Integer.parseInt(horseCountStr.trim());
            } catch (NumberFormatException e) {
                logger.error("Неверный формат количества лошадок для сделки {}: {}", leadId, horseCountStr);
                return false;
            }
            
            // Определяем параметры отправления
            int weight, length, width, height;
            if (horseCount == 1) {
                weight = HORSE_1_WEIGHT_GRAMS;
                length = HORSE_1_LENGTH;
                width = HORSE_1_WIDTH;
                height = HORSE_1_HEIGHT;
            } else if (horseCount == 3) {
                weight = HORSE_3_WEIGHT_GRAMS;
                length = HORSE_3_LENGTH;
                width = HORSE_3_WIDTH;
                height = HORSE_3_HEIGHT;
            } else {
                logger.warn("Неподдерживаемое количество лошадок: {}. Поддерживаются только 1 или 3", horseCount);
                // Используем параметры для 3 лошадок как дефолт для большего количества
                weight = HORSE_3_WEIGHT_GRAMS;
                length = HORSE_3_LENGTH;
                width = HORSE_3_WIDTH;
                height = HORSE_3_HEIGHT;
            }
            
            // Получаем контакт
            Long contactId = amoCrmService.getContactIdFromLead(leadId);
            if (contactId == null) {
                logger.error("Не удалось получить контакт для сделки {}", leadId);
                return false;
            }
            
            AmoContact contact = amoCrmService.getContact(contactId);
            if (contact == null) {
                logger.error("Не удалось получить данные контакта {}", contactId);
                return false;
            }
            
            // Получаем ПВЗ у контакта
            String pvz = contact.getCustomFieldValue(AmoCrmFieldId.CONTACT_PVZ.getId());
            if (pvz == null || pvz.trim().isEmpty()) {
                logger.error("Не указан ПВЗ у контакта {} для сделки {}", contactId, leadId);
                return false;
            }
            
            logger.info("Параметры доставки: количество={}, вес={}г, размеры={}x{}x{}см, ПВЗ={}", 
                    horseCount, weight, length, width, height, pvz);
            
            // Пытаемся извлечь город из ПВЗ или используем ПВЗ как есть
            // ПВЗ может быть в формате "Город, адрес" или просто адрес
            String toCity = extractCityFromPvz(pvz);
            Integer toPostalCode = extractPostalCodeFromPvz(pvz);
            
            // Рассчитываем доставку через СДЭК
            CdekDeliveryCalculatorService.DeliveryCalculationResult result = 
                    cdekDeliveryCalculatorService.calculateDelivery(toCity, toPostalCode, weight, length, width, height);
            
            if (!result.isSuccess()) {
                logger.error("Ошибка расчета доставки для сделки {}: {}", leadId, result.getError());
                // Все равно добавляем примечание с ошибкой
                String errorNote = String.format(
                        "Расчет доставки СДЭК:\nКоличество лошадок: %d\nПВЗ получателя: %s\nОшибка: %s",
                        horseCount, pvz, result.getError()
                );
                amoCrmService.addNoteToLead(leadId, errorNote);
                return false;
            }
            
            // Формируем текст примечания
            StringBuilder noteText = new StringBuilder();
            noteText.append("Расчет доставки СДЭК:\n");
            noteText.append("Количество лошадок: ").append(horseCount).append("\n");
            noteText.append("Параметры отправления: ").append(weight / 1000.0).append(" кг, ")
                    .append(length).append("x").append(width).append("x").append(height).append(" см\n");
            noteText.append("ПВЗ получателя: ").append(pvz).append("\n");
            noteText.append("Отправка из: ул. Трефолева, 9, корп. 2, Санкт-Петербург\n");
            noteText.append("\n").append(result.getFormattedResult());
            
            // Добавляем примечание к сделке
            boolean success = amoCrmService.addNoteToLead(leadId, noteText.toString());
            
            if (success) {
                logger.info("Успешно добавлено примечание с расчетом доставки для сделки {}", leadId);
            } else {
                logger.error("Не удалось добавить примечание для сделки {}", leadId);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("Ошибка при расчете доставки для сделки {}: {}", leadId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Извлекает город из строки ПВЗ
     */
    private String extractCityFromPvz(String pvz) {
        if (pvz == null || pvz.trim().isEmpty()) {
            return null;
        }
        
        // Пытаемся найти город (обычно в начале строки до запятой)
        String[] parts = pvz.split(",");
        if (parts.length > 0) {
            String firstPart = parts[0].trim();
            // Если первая часть похожа на город (не содержит цифр или содержит известные города)
            if (!firstPart.matches(".*\\d+.*")) {
                return firstPart;
            }
        }
        
        // Если не нашли, возвращаем null - API СДЭК попробует определить по другим параметрам
        return null;
    }
    
    /**
     * Извлекает почтовый индекс из строки ПВЗ (если есть)
     */
    private Integer extractPostalCodeFromPvz(String pvz) {
        if (pvz == null || pvz.trim().isEmpty()) {
            return null;
        }
        
        // Ищем 6-значный почтовый индекс
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b\\d{6}\\b");
        java.util.regex.Matcher matcher = pattern.matcher(pvz);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                // Игнорируем
            }
        }
        
        return null;
    }
}



