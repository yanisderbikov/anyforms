package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.CostBreakdown;
import ru.anyforms.dto.calculator.PriceBreakdown;
import ru.anyforms.dto.calculator.PriceInput;

public interface PriceCalculator {

    PriceBreakdown price(PriceInput input, CalculatorRates rates);

    CostBreakdown cost(PriceInput input, PriceBreakdown price, CalculatorRates rates);
}
