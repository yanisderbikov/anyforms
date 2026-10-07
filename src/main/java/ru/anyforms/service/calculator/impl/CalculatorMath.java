package ru.anyforms.service.calculator.impl;

import ru.anyforms.dto.calculator.RateStep;

import java.util.List;

final class CalculatorMath {

    private CalculatorMath() {
    }

    static double rub(double value) {
        return Math.round(value * 100) / 100.0;
    }

    static double roundToStep(double value, double step) {
        double normalized = rub(value);
        if (step <= 0) {
            return normalized;
        }
        return Math.round(normalized / step) * step;
    }

    static double stepValue(List<RateStep> steps, int tirage) {
        return steps.get(stepIndex(steps, tirage)).value();
    }

    static int stepIndex(List<RateStep> steps, int tirage) {
        int index = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (tirage >= steps.get(i).from()) {
                index = i;
            }
        }
        return index;
    }

    static double share(Double percent) {
        return percent == null ? 0 : percent / 100.0;
    }

    static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
