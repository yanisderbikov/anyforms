package ru.anyforms.event;

import java.time.Instant;

/**
 * Изменение по кастомным позициям, которое дублируется примечанием в сделку АМО.
 * text — готовый текст примечания; время по МСК слушатель добавляет сам из {@code at}.
 * author — кто внёс изменение (null — система: планировщик, доставка).
 */
public record CustomProductAmoNoteEvent(Long leadId, Instant at, String author, String text) {
}
