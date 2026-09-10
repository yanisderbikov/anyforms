package ru.anyforms.service.salesbot;

import java.util.List;

/**
 * Поставщик упорядоченной по {@code position} последовательности ботов группы
 * (таблица {@code bot_sequence}). Маленький порт (ISP).
 */
public interface BotSequenceProvider {

    /**
     * @param groupId группа
     * @return шаги цепочки, ОТСОРТИРОВАННЫЕ по возрастанию позиции; пустой список, если ботов нет.
     */
    List<BotStep> sequenceFor(Long groupId);
}
