package ru.anyforms.service.calculator.impl;

import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.service.calculator.CalculatorRatesService;
import ru.anyforms.service.calculator.OrderCalculatorService;

public final class CalculatorTestSupport {

    private CalculatorTestSupport() {
    }

    public static CalculatorRates seedRates() {
        return SeedRates.load();
    }

    public static OrderCalculatorService orderCalculator(CalculatorRatesService ratesService) {
        return new OrderCalculatorServiceImpl(new PriceCalculatorImpl(), new FormulaParameterEstimator(), ratesService);
    }
}
