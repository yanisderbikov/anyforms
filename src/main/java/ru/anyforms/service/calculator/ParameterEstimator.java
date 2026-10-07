package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.CalculationPositionRequest;
import ru.anyforms.dto.calculator.CalculationVariantRequest;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.model.calculator.SiliconeType;

public interface ParameterEstimator {

    ResolvedVariant resolve(CalculationPositionRequest position,
                            CalculationVariantRequest variant,
                            SiliconeType silicone,
                            int tirage,
                            CalculatorRates rates);
}
