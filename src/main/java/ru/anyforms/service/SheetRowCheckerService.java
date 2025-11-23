package ru.anyforms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SheetRowCheckerService {
    private static final Logger logger = LoggerFactory.getLogger(SheetRowCheckerService.class);
    
    private final GoogleSheetsService googleSheetsService;
    private final CdekTrackingService cdekTrackingService;
    
    // Индексы колонок (0-based: A=0, B=1, ..., F=5, I=8, J=9)
    private static final int COLUMN_F_INDEX = 5;  // Колонка F
    private static final int COLUMN_I_INDEX = 8;  // Колонка I (трекер)
    private static final int COLUMN_J_INDEX = 9;  // Колонка J
    
    // Слова, которые не должны быть в колонке F
    private static final String[] EXCLUDED_DELIVERY_TYPES = {"Лично", "курьер", "Курьер", "личная", "Личная"};
    
    // Текущий индекс строки для проверки
    private final AtomicInteger currentRowIndex = new AtomicInteger(1); // Начинаем с 1 (пропускаем заголовок)
    
    @Value("${google.sheets.sheet.name:Лошадка тест}")
    private String sheetName;

    public SheetRowCheckerService(GoogleSheetsService googleSheetsService, 
                                   CdekTrackingService cdekTrackingService) {
        this.googleSheetsService = googleSheetsService;
        this.cdekTrackingService = cdekTrackingService;
    }

    /**
     * Проверяет одну строку из таблицы
     * Вызывается периодически (раз в минуту)
     * Читает только последние 20 заполненных строк (игнорируя колонку K)
     */
    public void checkNextRow() {
        try {
            logger.info("Начало проверки строки из таблицы '{}'", sheetName);
            
            // Читаем последние 20 заполненных строк (игнорируя колонку K)
            List<List<Object>> filledRows = googleSheetsService.readLastFilledRows(sheetName, 20);
            
            if (filledRows == null || filledRows.isEmpty()) {
                logger.info("В таблице '{}' нет заполненных строк", sheetName);
                return;
            }
            
            int totalRows = filledRows.size();
            logger.debug("Найдено {} заполненных строк для проверки", totalRows);
            
            // Получаем текущий индекс строки для проверки
            int rowIndex = currentRowIndex.get();
            
            // Если дошли до конца списка, начинаем сначала
            if (rowIndex >= totalRows) {
                logger.info("Достигнут конец списка заполненных строк, начинаем сначала");
                rowIndex = 0;
                currentRowIndex.set(0);
            }
            
            // Проверяем текущую строку
            List<Object> row = filledRows.get(rowIndex);
            
            // Номер строки для логирования (примерный, так как мы работаем с последними 20 строками)
            logger.info("Проверка строки {} из последних {} заполненных строк таблицы '{}'", 
                    rowIndex + 1, totalRows, sheetName);
            
            // Проверяем условия
            if (checkRowConditions(row, rowIndex + 1)) {
                // Условия выполнены, обрабатываем строку
                processRow(row, rowIndex + 1);
            } else {
                logger.debug("Строка {} не соответствует условиям, пропускаем", rowIndex + 1);
            }
            
            // Переходим к следующей строке
            rowIndex++;
            if (rowIndex >= totalRows) {
                rowIndex = 0; // Начинаем сначала
            }
            currentRowIndex.set(rowIndex);
            
            logger.info("Проверка строки {} завершена. Следующая проверка начнется со строки {}", 
                    rowIndex, currentRowIndex.get() + 1);
            
        } catch (Exception e) {
            logger.error("Ошибка при проверке строки из таблицы '{}': {}", sheetName, e.getMessage(), e);
        }
    }

    /**
     * Проверяет условия для строки:
     * - Колонка I содержит трекер (набор цифр)
     * - Колонка F НЕ должна быть "Лично", "курьер" и т.п.
     * - Колонка J должна быть пустая
     */
    private boolean checkRowConditions(List<Object> row, int rowNumber) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        
        // Получаем значения из нужных колонок
        String columnF = googleSheetsService.getCellValue(row, COLUMN_F_INDEX);
        String columnI = googleSheetsService.getCellValue(row, COLUMN_I_INDEX);
        String columnJ = googleSheetsService.getCellValue(row, COLUMN_J_INDEX);
        
        // Проверка 1: Колонка I должна содержать валидный трекер
        if (columnI.isEmpty() || !cdekTrackingService.isValidTrackingNumber(columnI)) {
            logger.debug("Строка {}: колонка I не содержит валидный трекер (значение: '{}')", rowNumber, columnI);
            return false;
        }
        
        // Проверка 2: Колонка F не должна быть в списке исключений
        String columnFLower = columnF.toLowerCase();
        for (String excluded : EXCLUDED_DELIVERY_TYPES) {
            if (columnFLower.contains(excluded.toLowerCase())) {
                logger.debug("Строка {}: колонка F содержит исключенное значение '{}'", rowNumber, columnF);
                return false;
            }
        }
        
        // Проверка 3: Колонка J должна быть пустая
        if (!columnJ.isEmpty()) {
            logger.debug("Строка {}: колонка J не пуста (значение: '{}')", rowNumber, columnJ);
            return false;
        }
        
        logger.info("Строка {} соответствует всем условиям: F='{}', I='{}', J='{}'", 
                rowNumber, columnF, columnI, columnJ);
        return true;
    }

    /**
     * Обрабатывает строку: проверяет статус трекера в СДЭК и логирует результат
     */
    private void processRow(List<Object> row, int rowNumber) {
        String trackingNumber = googleSheetsService.getCellValue(row, COLUMN_I_INDEX);
        
        logger.info("Обработка строки {}: проверка статуса трекера СДЭК {}", rowNumber, trackingNumber);
        
        String status = cdekTrackingService.checkTrackingStatus(trackingNumber);
        
        if (status != null) {
            logger.info("СТАТУС ТРЕКЕРА СДЭК: Строка {}, Трекер: {}, Статус: {}", 
                    rowNumber, trackingNumber, status);
        } else {
            logger.warn("Не удалось получить статус для трекера СДЭК: Строка {}, Трекер: {}", 
                    rowNumber, trackingNumber);
        }
    }
}

