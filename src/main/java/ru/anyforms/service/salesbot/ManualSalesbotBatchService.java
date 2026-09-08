package ru.anyforms.service.salesbot;

import ru.anyforms.dto.RunSalesbotBatchRequestDTO;
import ru.anyforms.dto.salesbot.ManualRunDTO;
import ru.anyforms.dto.salesbot.ManualRunPreviewDTO;

import java.util.List;

/**
 * Ручной массовый запуск бота из админки: превью (сколько лидов получат бота), старт
 * фонового прогона и список последних запусков.
 */
public interface ManualSalesbotBatchService {

    /** Считает лидов статуса и сколько из них уже получали этого бота. Синхронно ходит в amoCRM. */
    ManualRunPreviewDTO preview(Long pipelineId, Long statusId, Long botId, String tagName);

    /**
     * Стартует прогон в фоне и сразу возвращает запись с нулевыми счётчиками.
     *
     * @throws org.springframework.web.server.ResponseStatusException 409, если другой запуск ещё идёт
     */
    ManualRunDTO start(RunSalesbotBatchRequestDTO request, String startedBy);

    /** Последние запуски, новые сверху (только за время жизни процесса). */
    List<ManualRunDTO> recent();
}
