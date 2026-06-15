package ru.anyforms.model.salesbot;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Слот глобального расписания (каденции). Одно расписание на все типы заказов.
 * Время хранится в UTC; на рантайме к нему добавляется случайный джиттер
 * (см. {@code ru.anyforms.service.salesbot.JitterProvider}), чтобы время чуть
 * «плавало» от недели к неделе и выглядело по-человечески.
 * <p>
 * Базовые времена держим «некруглыми» (напр. 07:38, 14:06 UTC). Менять можно
 * прямо в таблице без передеплоя.
 */
@Entity
@Table(name = "schedule")
@Data
public class ScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** День недели по ISO-8601: 1 = понедельник ... 7 = воскресенье ({@link java.time.DayOfWeek#getValue()}). */
    @Column(name = "weekday", nullable = false)
    private Integer weekday;

    /**
     * Базовое время слота как {@link Instant} (timestamptz). Значима только UTC-составляющая
     * времени суток — дата является плейсхолдером (1970-01-01). Джиттер применяется на рантайме.
     */
    @Column(name = "time_utc", nullable = false)
    private Instant timeUtc;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
