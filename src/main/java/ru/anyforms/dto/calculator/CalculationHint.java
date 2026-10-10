package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Подсказка или предупреждение калькулятора")
public record CalculationHint(
        @Schema(description = "Код подсказки", example = "MIN_FORM_PRICE") String code,
        Level level,
        String message) {

    public enum Level {
        INFO,
        WARNING,
        ERROR
    }

    public static CalculationHint info(String code, String message) {
        return new CalculationHint(code, Level.INFO, message);
    }

    public static CalculationHint warning(String code, String message) {
        return new CalculationHint(code, Level.WARNING, message);
    }

    public static CalculationHint error(String code, String message) {
        return new CalculationHint(code, Level.ERROR, message);
    }
}
