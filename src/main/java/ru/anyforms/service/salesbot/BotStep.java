package ru.anyforms.service.salesbot;

/**
 * Один шаг цепочки: какой бот ({@code botId}), на какой позиции ({@code position}) и не раньше
 * чем через сколько минут после предыдущего шага ({@code delayMinutes}). Значение-объект.
 *
 * @param botId        ID SalesBot в amoCRM
 * @param position     порядковый номер в цепочке (1-based)
 * @param delayMinutes задержка от якоря (предыдущий шаг / первое появление в статусе), минуты
 */
public record BotStep(Long botId, Integer position, Integer delayMinutes) {

    /** Шаг без задержки (служебные запуски, тесты). */
    public BotStep(Long botId, Integer position) {
        this(botId, position, 0);
    }
}
