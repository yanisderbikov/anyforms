package ru.anyforms.model.salesbot;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Порядок ботов для каждого {@link OrderType}.
 * <p>
 * {@code position} — порядковый номер, начиная с 1. Если в последовательность
 * добавить новую позицию (была 9 — стала 10), она автоматически станет
 * «следующей» для всех лидов, у которых позиции 1–9 уже отработали успешно.
 */
@Entity
@Table(
        name = "bot_sequence",
        uniqueConstraints = @UniqueConstraint(name = "uq_bot_sequence_type_position", columnNames = {"type", "position"})
)
@Data
public class BotSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bot_id", nullable = false)
    private Long botId;

    /** Порядковый номер бота в цепочке данного типа, начиная с 1. */
    @Column(name = "position", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrderType type;
}
