package ru.anyforms.service.salesbot;

/**
 * Группа, участвующая в прогоне дрип-кампании: включена и с заданной воронкой/статусом.
 *
 * @param id     ID группы ({@code bot_group.id})
 * @param name   название (для логов)
 * @param target воронка/статус, где искать лидов
 * @param window окно отправки по Москве (своё или по умолчанию)
 */
public record ActiveGroup(Long id, String name, FunnelTarget target, TimeWindow window) {
}
