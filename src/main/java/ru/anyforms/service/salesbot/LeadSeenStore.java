package ru.anyforms.service.salesbot;

import java.time.Instant;

/**
 * Якорь первого шага: когда прогон впервые увидел сделку в статусе группы
 * (таблица {@code bot_group_lead_seen}). Маленький порт (ISP).
 */
public interface LeadSeenStore {

    /**
     * Возвращает момент первого появления сделки в статусе группы; если записи ещё нет —
     * записывает {@code now} и возвращает его.
     */
    Instant firstSeenOrRecord(Long groupId, Long leadId, Instant now);
}
