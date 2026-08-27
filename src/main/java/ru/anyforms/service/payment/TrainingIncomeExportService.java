package ru.anyforms.service.payment;

public interface TrainingIncomeExportService {
    /**
     * Дописывает недостающие продажи обучения в гугл-таблицу доходов.
     *
     * @return сколько строк дописали
     */
    int export();
}
