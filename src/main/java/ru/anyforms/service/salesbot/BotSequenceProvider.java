package ru.anyforms.service.salesbot;

import ru.anyforms.model.salesbot.OrderType;

import java.util.List;

/**
 * Поставщик упорядоченной по {@code position} последовательности ботов для типа
 * (таблица {@code bot_sequence}). Маленький порт (ISP).
 */
public interface BotSequenceProvider {

    /**
     * @param type тип заказа
     * @return шаги цепочки, ОТСОРТИРОВАННЫЕ по возрастанию позиции (1, 2, 3, ...);
     *         пустой список, если для типа ботов нет.
     */
    List<BotStep> sequenceFor(OrderType type);
}
