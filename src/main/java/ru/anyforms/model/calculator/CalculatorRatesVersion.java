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
@Table(name = "calculator_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CalculatorRatesVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rates", nullable = false, columnDefinition = "TEXT")
    private String rates;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by_email")
    private String createdByEmail;

    @Column(name = "created_by_name")
    private String createdByName;
}
