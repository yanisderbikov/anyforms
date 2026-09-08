package ru.anyforms.model.amo;

/**
 * Статус (колонка) воронки amoCRM из {@code GET /api/v4/leads/pipelines}.
 *
 * @param id   ID статуса ({@code status_id})
 * @param name название колонки
 * @param sort порядок в воронке; может быть {@code null}
 */
public record AmoPipelineStatusInfo(Long id, String name, Integer sort) {
}
