package ru.anyforms.model.salesbot;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Порядок ботов в группе ({@link BotGroup}).
 * <p>
 * {@code position} — порядковый номер, начиная с 1; пропуски допустимы (после удаления
 * шага остальные не сдвигаются). Новый бот встаёт в конец, порядок меняется перестановкой
 * соседей. Если добавить новую старшую позицию, она автоматически станет «следующей» для
 * всех лидов, у которых предыдущие уже отработали успешно.
 */
@Entity
@Table(
        name = "bot_sequence",
        uniqueConstraints = @UniqueConstraint(name = "uq_bot_sequence_group_position", columnNames = {"group_id", "position"})
)
@Data
public class BotSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "bot_id", nullable = false)
    private Long botId;

    /** Порядковый номер бота в цепочке группы, начиная с 1. */
    @Column(name = "position", nullable = false)
    private Integer position;

    /**
     * Не раньше чем через столько минут после предыдущего шага (для шага 1 — после того, как
     * сделка впервые замечена в статусе группы). Так у разных групп разный ритм, и сообщения
     * не уходят всем в одно и то же время.
     */
    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes = 1440;
}
