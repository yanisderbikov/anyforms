package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.CalculationHint;
import ru.anyforms.dto.calculator.ParameterSource;
import ru.anyforms.dto.calculator.PriceInput;

import java.util.List;
import java.util.Map;

public record ResolvedVariant(PriceInput input, Map<String, ParameterSource> sources, List<CalculationHint> hints) {
}
