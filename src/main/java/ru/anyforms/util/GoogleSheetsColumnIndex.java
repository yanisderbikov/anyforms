package ru.anyforms.util;

/**
 * Константы для индексов колонок Google таблицы (0-based: A=0, B=1, ..., Z=25)
 * Столбцы таблиц не меняются, поэтому вынесены в отдельный класс
 */
public final class GoogleSheetsColumnIndex {
    
    private GoogleSheetsColumnIndex() {
        // Утилитный класс, не должен быть инстанциирован
    }
    
    /** Колонка E (ссылка на сделку) */
    public static final int COLUMN_E_INDEX = 4;
    
    /** Колонка F */
    public static final int COLUMN_F_INDEX = 5;
    
    /** Колонка I (трекер) */
    public static final int COLUMN_I_INDEX = 8;
    
    /** Колонка J (статус) */
    public static final int COLUMN_J_INDEX = 9;
    
    /** Колонка K */
    public static final int COLUMN_K_INDEX = 10;
}

