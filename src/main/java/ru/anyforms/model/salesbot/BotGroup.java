package ru.anyforms.model.salesbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Группа дрип-кампании: название, где искать лидов (воронка/статус amoCRM) и цепочка ботов
 * ({@link BotSequence} по {@code group_id}). Без воронки или с {@code enabled = false}
 * группа в прогоне не участвует.
 */
@Entity
@Table(name = "bot_group")
@Data
public class BotGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "pipeline_id")
    private Long pipelineId;

    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Окно отправки, начало: {@link Instant} с датой-плейсхолдером {@link #WINDOW_DATE},
     * значимо только время суток по Москве (как раньше слоты {@code schedule}).
     * {@code null} — окно по умолчанию.
     */
    @Column(name = "send_from")
    private Instant sendFrom;

    /** Окно отправки, конец (исключительно), тот же формат. */
    @Column(name = "send_to")
    private Instant sendTo;

    /** Зона окна отправки и дата-плейсхолдер для хранения времени суток как Instant. */
    public static final ZoneId WINDOW_ZONE = ZoneId.of("Europe/Moscow");
    public static final LocalDate WINDOW_DATE = LocalDate.of(1970, 1, 1);

    public boolean hasSendWindow() {
        return sendFrom != null && sendTo != null;
    }

    /** Начало окна как время суток по Москве. */
    public LocalTime sendFromTime() {
        return sendFrom == null ? null : sendFrom.atZone(WINDOW_ZONE).toLocalTime();
    }

    /** Конец окна как время суток по Москве. */
    public LocalTime sendToTime() {
        return sendTo == null ? null : sendTo.atZone(WINDOW_ZONE).toLocalTime();
    }

    public void setSendWindow(LocalTime from, LocalTime to) {
        this.sendFrom = from == null ? null : toInstant(from);
        this.sendTo = to == null ? null : toInstant(to);
    }

    private static Instant toInstant(LocalTime time) {
        return WINDOW_DATE.atTime(time).atZone(WINDOW_ZONE).toInstant();
    }

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Воронка и статус заданы — группу можно гонять. */
    public boolean hasFunnel() {
        return pipelineId != null && statusId != null;
    }
}
