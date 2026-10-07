package ru.anyforms.model.calculator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "order_calculations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"request", "result"})
public class OrderCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client")
    private String client;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "total_rub")
    private Double totalRub;

    @Column(name = "margin")
    private Double margin;

    @Column(name = "preliminary", nullable = false)
    private boolean preliminary;

    @Column(name = "has_estimates", nullable = false)
    private boolean hasEstimates;

    @Column(name = "has_exceptions", nullable = false)
    private boolean hasExceptions;

    @Column(name = "below_min_margin", nullable = false)
    private boolean belowMinMargin;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "request", nullable = false, columnDefinition = "TEXT")
    private String request;

    @Column(name = "result", nullable = false, columnDefinition = "TEXT")
    private String result;

    @Column(name = "rates_id")
    private Long ratesId;

    @Column(name = "created_by_email", nullable = false)
    private String createdByEmail;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
