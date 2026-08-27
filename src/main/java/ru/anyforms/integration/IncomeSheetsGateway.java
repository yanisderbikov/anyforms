package ru.anyforms.integration;

import java.util.List;

/**
 * Гугл-таблица доходов (отдельная от таблицы заказов, см. GOOGLE_SHEETS_INCOME_SPREADSHEET_ID)
 */
public interface IncomeSheetsGateway {
    /**
     * Создаёт лист с заголовком, если его ещё нет
     */
    void ensureSheetExists(String sheetName, List<Object> headerRow);

    /**
     * Читает все строки из указанного листа
     */
    List<List<Object>> readAllRows(String sheetName);

    /**
     * Дописывает строки после последней заполненной
     */
    void appendRows(String sheetName, List<List<Object>> rows);
}
