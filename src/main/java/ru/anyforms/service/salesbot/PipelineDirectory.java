package ru.anyforms.service.salesbot;

import ru.anyforms.model.amo.AmoPipelineInfo;

import java.util.List;
import java.util.Map;

/**
 * Справочник воронок и статусов аккаунта amoCRM для админки: выбор воронки/статуса по имени
 * и подписи к id в настройках и ручных запусках. Кэшируется, как и {@link SalesbotDirectory}.
 */
public interface PipelineDirectory {

    /**
     * Все воронки со статусами. При {@code refresh} кэш игнорируется.
     *
     * @throws IllegalStateException если amoCRM недоступен и кэша ещё нет
     */
    List<AmoPipelineInfo> pipelines(boolean refresh);

    /** {@code pipeline_id → название}; при недоступности amoCRM — пустая карта. */
    Map<Long, String> pipelineNames();

    /**
     * {@code status_id → название}. Системные статусы 142/143 в каждой воронке одинаковы,
     * поэтому плоская карта корректна. При недоступности amoCRM — пустая карта.
     */
    Map<Long, String> statusNames();
}
