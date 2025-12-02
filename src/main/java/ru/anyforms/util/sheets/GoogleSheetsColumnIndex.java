package ru.anyforms.util.sheets;

/**
 * Константы для индексов колонок Google таблицы (0-based: A=0, B=1, ..., Z=25)
 * Столбцы таблиц не меняются, поэтому вынесены в отдельный класс
 */
public final class GoogleSheetsColumnIndex {
    
    private GoogleSheetsColumnIndex() {
        // Утилитный класс, не должен быть инстанциирован
    }
    
    /** Колонка A (ФИО) */
    public static final int COLUMN_A_INDEX = 0;
    
    /** Колонка B (Количество) */
    public static final int COLUMN_B_INDEX = 1;
    
    /** Колонка C (Телефон) */
    public static final int COLUMN_C_INDEX = 2;
    
    /** Колонка E (ссылка на сделку) */
    public static final int COLUMN_E_INDEX = 4;
    
    /** Колонка F (ПВЗ СДЭК) */
    public static final int COLUMN_F_INDEX = 5;
    
    /** Колонка G (Дата оплаты) */
    public static final int COLUMN_G_INDEX = 6;
    
    /** Колонка I (трекер) */
    public static final int COLUMN_I_INDEX = 8;
    
    /** Колонка J (статус) */
    public static final int COLUMN_J_INDEX = 9;
    
    /** Колонка K */
    public static final int COLUMN_K_INDEX = 10;
}

