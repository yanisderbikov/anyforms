package ru.anyforms.model.salesbot;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Маппинг {@link OrderType} → воронка/статус в amoCRM.
 * Из этой таблицы берутся {@code pipeline_id} и {@code status_id} для запроса №1
 * (выборка лидов в целевом статусе).
 */
@Entity
@Table(
        name = "order_type_funnel",
        uniqueConstraints = @UniqueConstraint(name = "uq_order_type_funnel_type", columnNames = "type")
)
@Data
public class OrderTypeFunnel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrderType type;

    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    @Column(name = "status_id", nullable = false)
    private Long statusId;
}
