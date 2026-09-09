package ru.anyforms.model.salesbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.Instant;

/**
 * Когда прогон впервые увидел сделку в статусе группы — якорь для задержки первого шага.
 * Пишется один раз на пару (группа, сделка).
 */
@Entity
@Table(name = "bot_group_lead_seen",
        uniqueConstraints = @UniqueConstraint(name = "uq_bot_group_lead_seen", columnNames = {"group_id", "lead_id"}))
@Data
public class BotGroupLeadSeen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;
}
