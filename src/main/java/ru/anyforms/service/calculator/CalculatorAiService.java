package ru.anyforms.service.calculator;

import ru.anyforms.dto.calculator.AiSuggestionRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiSuggestion;

public interface CalculatorAiService {

    boolean available();

    String providerName();

    CalculationAiSuggestion suggest(AiSuggestionRequest request);
}
