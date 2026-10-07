package ru.anyforms.integration;

import ru.anyforms.dto.calculator.ai.CalculationAiRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiSuggestion;

public interface CalculationAiGateway {

    String providerName();

    CalculationAiSuggestion suggestParameters(CalculationAiRequest request);
}
