package ru.anyforms.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.service.payment.TrainingIncomeExportService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingIncomeExportTask {

    private final TrainingIncomeExportService trainingIncomeExportService;

    /**
     * Ночная выгрузка продаж обучения в гугл-таблицу доходов.
     * 4:00 МСК — чтобы не нагружать дневные процессы
     */
    @Scheduled(
            cron = "0 0 4 * * *",
            zone = "Europe/Moscow"
    )
    public void process() {
        try {
            trainingIncomeExportService.export();
        } catch (Exception e) {
            log.error("Шедулер выгрузки доходов обучения не сработал", e);
        }
    }
}
