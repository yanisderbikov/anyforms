package ru.anyforms.service.salesbot;

import java.util.List;
import java.util.Optional;

/** Реестр ручных запусков (последние N, в памяти). */
public interface ManualRunRegistry {

    ManualRun create(Long pipelineId, Long statusId, Long botId, String tagName, String startedBy);

    /** Последние запуски, новые сверху. */
    List<ManualRun> recent();

    /** Идущий сейчас запуск, если есть: одновременно допускается только один. */
    Optional<ManualRun> running();
}
