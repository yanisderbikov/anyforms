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

    /**
     * Считает лидов статуса (с отбором по тегу и признаку «Розница») и сколько из них уже
     * получали этого бота. Синхронно ходит в amoCRM.
     *
     * @param botId  бот; {@code null} — посчитать только лидов, без «уже получали»
     * @param retail {@code true} — только розница, {@code false} — только не розница, {@code null} — любые
     */
    ManualRunPreviewDTO preview(Long pipelineId, Long statusId, Long botId, String tagName, Boolean retail);

    /**
     * Стартует прогон в фоне и сразу возвращает запись с нулевыми счётчиками.
     *
     * @throws org.springframework.web.server.ResponseStatusException 409, если другой запуск ещё идёт
     */
    ManualRunDTO start(RunSalesbotBatchRequestDTO request, String startedBy);

    /** Последние запуски, новые сверху (только за время жизни процесса). */
    List<ManualRunDTO> recent();
}
