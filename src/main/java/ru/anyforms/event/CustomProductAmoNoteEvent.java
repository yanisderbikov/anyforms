package ru.anyforms.event;

import java.time.Instant;
import java.util.List;

/**
 * Изменение по кастомным позициям, которое дублируется примечанием в сделку АМО.
 * title — что произошло, lines — подробности (может быть пустым).
 */
public record CustomProductAmoNoteEvent(Long leadId, Instant at, String title, List<String> lines) {
}
