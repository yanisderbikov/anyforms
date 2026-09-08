package ru.anyforms.model.amo;

import java.util.List;

/**
 * Воронка amoCRM с её статусами ({@code GET /api/v4/leads/pipelines}) — для выбора
 * воронки/статуса по имени в админке. Не путать с enum {@link AmoPipeline} (захардкоженные id).
 *
 * @param id       ID воронки ({@code pipeline_id})
 * @param name     название воронки
 * @param sort     порядок среди воронок; может быть {@code null}
 * @param statuses статусы по порядку колонок
 */
public record AmoPipelineInfo(Long id, String name, Integer sort, List<AmoPipelineStatusInfo> statuses) {
}
