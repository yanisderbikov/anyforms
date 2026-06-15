package ru.anyforms.service.salesbot;

/**
 * Один шаг цепочки: какой бот ({@code botId}) и на какой позиции ({@code position})
 * должен уехать лиду. Значение-объект (immutable).
 *
 * @param botId    ID SalesBot в amoCRM
 * @param position порядковый номер в цепочке (1-based)
 */
public record BotStep(Long botId, Integer position) {
}
