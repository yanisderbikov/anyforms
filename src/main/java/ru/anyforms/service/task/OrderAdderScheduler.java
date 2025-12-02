package ru.anyforms.service.task;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.SyncOrderRequestDTO;
import ru.anyforms.integration.GoogleSheetsGateway;
import ru.anyforms.service.DeliveryProcessor;
import ru.anyforms.util.LeadUrlExtractor;
import ru.anyforms.util.amo.DataFormattingService;
import ru.anyforms.service.OrderService;
import ru.anyforms.util.sheets.GoogleSheetsColumnIndex;

import java.util.*;

@Log4j2
@Component
@AllArgsConstructor
public class OrderAdderScheduler {

    private final GoogleSheetsGateway googleSheetsService;
    private final OrderService orderService;
    private final DeliveryProcessor deliveryProcessor;
    private final DataFormattingService dataFormattingService;

    // Список всех таблиц для обработки
    private static final List<String> SHEET_NAMES = Arrays.asList(
            "Лошадка",
            "Самовар",
            "Щелкунчик",
            "Елочка",
            "Комплект",
            "Мышиный король",
            "Круасан текст больш",
            "Масло",
            "Осьминожка",
            "Олененок",
            "Зайка"
    );

    public void updateAllUntrackedOrders() {
        for (var leadId : getAllUntrackedLeads()) {
            orderService.syncOrder(new SyncOrderRequestDTO(leadId));
        }
    }


    /**
     * Получает список всех leadId из таблицы "Лошадка", у которых нет трекера
     * @return список leadId без трекера
     */
    private List<Long> getAllUntrackedLeads() {
        List<Long> untrackedLeads = new ArrayList<>();
        String sheetName = "Лошадка";
        
        try {
            // Читаем все строки из таблицы
            List<List<Object>> allRows = googleSheetsService.readAllRows(sheetName);
            
            if (allRows == null || allRows.isEmpty()) {
                log.debug("Таблица '{}' пуста", sheetName);
                return untrackedLeads;
            }
            
            // Пропускаем заголовок (первая строка), начинаем со второй строки
            for (int i = 1; i < allRows.size(); i++) {
                List<Object> row = allRows.get(i);
                
                // Проверяем, что строка заполнена (игнорируя колонку K)
                if (!googleSheetsService.isRowFilled(row)) {
                    continue;
                }
                
                // Проверяем, есть ли трекер в колонке I
                String tracker = googleSheetsService.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_I_INDEX);
                if (tracker != null && !tracker.trim().isEmpty()) {
                    // У этого заказа уже есть трекер, пропускаем
                    continue;
                }
                
                // Получаем ссылку на сделку из столбца E
                String dealLink = googleSheetsService.getCellValue(row, GoogleSheetsColumnIndex.COLUMN_E_INDEX);
                if (dealLink == null || dealLink.trim().isEmpty()) {
                    log.debug("Строка {} таблицы '{}' не содержит ссылку на сделку в столбце E, пропускаем", 
                            i + 1, sheetName);
                    continue;
                }
                
                // Извлекаем ID сделки из ссылки
                Long leadId = LeadUrlExtractor.extract(dealLink);
                if (leadId == null) {
                    log.warn("Не удалось извлечь ID сделки из ссылки: {} в строке {} таблицы '{}'", 
                            dealLink, i + 1, sheetName);
                    continue;
                }
                
                // Добавляем leadId в список
                untrackedLeads.add(leadId);
            }
            
            log.info("Найдено {} заказов без трекера в таблице '{}'", untrackedLeads.size(), sheetName);
            
        } catch (Exception e) {
            log.error("Ошибка при получении списка заказов без трекера из таблицы '{}': {}", 
                    sheetName, e.getMessage(), e);
        }
        
        return untrackedLeads;
    }
}


