package ru.anyforms.service.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.service.SheetRowCheckerService;

@Component
public class SheetRowCheckerScheduler {
    private static final Logger logger = LoggerFactory.getLogger(SheetRowCheckerScheduler.class);
    
    private final SheetRowCheckerService sheetRowCheckerService;

    public SheetRowCheckerScheduler(SheetRowCheckerService sheetRowCheckerService) {
        this.sheetRowCheckerService = sheetRowCheckerService;
    }

    /**
     * Периодическая проверка строк в Google Sheets
     * Выполняется каждую минуту (60000 миллисекунд)
     */
    @Scheduled(fixedRate = 3_600_000) // 1 минута = 60000 мс
    public void checkSheetRows() {
        logger.debug("Запуск периодической проверки строк в таблице");
        sheetRowCheckerService.checkNextRow();
    }
}

