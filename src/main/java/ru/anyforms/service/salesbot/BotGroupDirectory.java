package ru.anyforms.service.salesbot;

import java.util.List;

/**
 * Источник групп для прогона (таблица {@code bot_group}). Маленький порт (ISP):
 * оркестратор зависит только от него, не зная про БД.
 */
public interface BotGroupDirectory {

    /** Группы, которые надо обработать в прогоне: включённые и с воронкой/статусом. */
    List<ActiveGroup> activeGroups();
}
