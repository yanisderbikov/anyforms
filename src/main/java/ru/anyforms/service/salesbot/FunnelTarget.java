package ru.anyforms.service.salesbot;

/**
 * Целевая «координата» лида в amoCRM — пара воронка/статус, в которой должна
 * идти дрип-кампания. Значение-объект (immutable).
 *
 * @param pipelineId ID воронки в amoCRM
 * @param statusId   ID статуса в amoCRM
 */
public record FunnelTarget(Long pipelineId, Long statusId) {
}
