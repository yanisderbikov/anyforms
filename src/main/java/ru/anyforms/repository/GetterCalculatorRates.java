package ru.anyforms.repository;

import ru.anyforms.model.calculator.CalculatorRatesVersion;

import java.util.List;
import java.util.Optional;

public interface GetterCalculatorRates {
    Optional<CalculatorRatesVersion> getLatest();

    List<CalculatorRatesVersion> getHistory(int limit);
}
