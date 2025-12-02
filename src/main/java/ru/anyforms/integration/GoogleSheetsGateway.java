package ru.anyforms.integration;

import java.util.List;

/**
 * Интерфейс для работы с Google Sheets API
 */
public interface GoogleSheetsGateway {
    /**
     * Добавляет строку в таблицу (использует лист по умолчанию)
     */
    void appendRow(List<Object> rowData);

    /**
     * Добавляет строку в указанный лист
     */
    void appendRow(List<Object> rowData, String sheetName);

    /**
     * Читает все строки из указанного листа
     */
    List<List<Object>> readAllRows(String sheetName);

    /**
     * Читает все строки из листа по умолчанию
     */
    List<List<Object>> readAllRows();

    /**
     * Получает значение ячейки из строки по индексу колонки
     */
    String getCellValue(List<Object> row, int columnIndex);

    /**
     * Проверяет, является ли строка заполненной (игнорируя колонку K)
     */
    boolean isRowFilled(List<Object> row);

    /**
     * Читает последние N заполненных строк из указанного листа
     */
    List<List<Object>> readLastFilledRows(String sheetName, int count);

    /**
     * Читает последние N заполненных строк из листа по умолчанию
     */
    List<List<Object>> readLastFilledRows(int count);

    /**
     * Записывает значение в конкретную ячейку таблицы
     */
    void writeCell(String sheetName, int rowNumber, int columnIndex, String value);

    /**
     * Записывает значение в конкретную ячейку листа по умолчанию
     */
    void writeCell(int rowNumber, int columnIndex, String value);

    /**
     * Находит строку по значению в указанной колонке и записывает значение в другую колонку
     */
    boolean findAndWriteCell(String sheetName, int searchColumnIndex, String searchValue, 
                             int writeColumnIndex, String writeValue);

    /**
     * Находит строку по значению в указанной колонке и записывает значение в другую колонку (лист по умолчанию)
     */
    boolean findAndWriteCell(int searchColumnIndex, String searchValue, 
                             int writeColumnIndex, String writeValue);
}

