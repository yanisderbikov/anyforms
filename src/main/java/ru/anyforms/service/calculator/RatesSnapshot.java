package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.CalculatorRates;

import java.time.Instant;

public record RatesSnapshot(Long versionId, CalculatorRates rates, Instant updatedAt, String updatedBy) {
}
