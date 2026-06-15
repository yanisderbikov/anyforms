package ru.anyforms.model.salesbot;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Журнал того, что и кому уже отработало — единственный источник истины о прогрессе цепочки.
 * <p>
 * {@code UNIQUE(lead_id, bot_id)} — жёсткий бэкстоп против двойной отправки одного и того же
 * бота одному и тому же лиду. Запись пишется через upsert
 * ({@code ON CONFLICT (lead_id, bot_id) DO UPDATE}), поэтому строка со статусом
 * {@link BotExecutionStatus#FAILED} (лид временно вышел из статуса) может позже быть
 * «повышена» до {@link BotExecutionStatus#SUCCESS}, когда бот реально уедет.
 *
 * <p>TODO(run_id): в v1 поля run_id нет. Следствие: если лид вышел и вернулся в статус,
 * он продолжит с места остановки, а не с начала. Если бизнес захочет рестарт цепочки
 * при повторном входе — добавить колонку {@code run_id} и считать «следующего» бота
 * в рамках текущего прогона.
 */
@Entity
@Table(
        name = "bot_execution_log",
        uniqueConstraints = @UniqueConstraint(name = "uq_bot_execution_log_lead_bot", columnNames = {"lead_id", "bot_id"})
)
@Data
public class BotExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Column(name = "bot_id", nullable = false)
    private Long botId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BotExecutionStatus status;

    /** Момент попытки запуска как {@link Instant} (timestamptz, UTC). */
    @Column(name = "date_executed", nullable = false)
    private Instant dateExecuted;
}
