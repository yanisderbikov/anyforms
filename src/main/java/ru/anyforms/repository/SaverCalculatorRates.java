package ru.anyforms.repository;

import ru.anyforms.model.calculator.CalculatorRatesVersion;

public interface SaverCalculatorRates {
    CalculatorRatesVersion save(CalculatorRatesVersion version);
}
