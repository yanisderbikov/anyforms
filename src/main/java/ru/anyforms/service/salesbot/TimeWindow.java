package ru.anyforms.service.salesbot;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Окно времени суток по Москве: {@code [from, to)}. Задаёт, в какие часы могут уходить боты.
 *
 * @param from начало (включительно)
 * @param to   конец (исключительно), позже начала
 */
public record TimeWindow(LocalTime from, LocalTime to) {

    public TimeWindow {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Окно отправки: нужны и начало, и конец");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Окно отправки: начало должно быть раньше конца (" + from + "-" + to + ")");
        }
    }

    /** Разбор строки вида {@code 09:00-21:00}. */
    public static TimeWindow parse(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Окно отправки: ожидается HH:mm-HH:mm, получено '" + text + "'");
        }
        try {
            return new TimeWindow(LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Окно отправки: ожидается HH:mm-HH:mm, получено '" + text + "'", e);
        }
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(from) && time.isBefore(to);
    }

    @Override
    public String toString() {
        return from + "-" + to;
    }
}
