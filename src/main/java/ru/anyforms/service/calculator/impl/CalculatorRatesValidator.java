package ru.anyforms.service.calculator.impl;

import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.RateStep;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class CalculatorRatesValidator {

    private CalculatorRatesValidator() {
    }

    static List<String> validate(CalculatorRates rates) {
        List<String> problems = new ArrayList<>();
        if (rates == null) {
            problems.add("Пустой справочник ставок");
            return problems;
        }
        for (Field field : CalculatorRates.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object value = read(field, rates);
            if (value == null) {
                problems.add("Не заполнена ставка " + field.getName());
            } else if (value instanceof Number number
                    && (Double.isNaN(number.doubleValue()) || Double.isInfinite(number.doubleValue()) || number.doubleValue() < 0)) {
                problems.add("Ставка " + field.getName() + " должна быть неотрицательным числом");
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }

        checkSteps("kitsByTirage", rates.getKitsByTirage(), true, problems);
        checkSteps("minFormPriceByTirage", rates.getMinFormPriceByTirage(), false, problems);
        if (rates.getTaxRate() >= 1) {
            problems.add("Налог должен быть меньше 100%");
        }
        if (rates.getTaxRate() + rates.getManagerCommission() + rates.getMinProfitShare() >= 1) {
            problems.add("Налог, комиссия менеджера и минимальная маржа вместе должны быть меньше 100%");
        }
        if (rates.getMaxKitsPaidByClient() < 1) {
            problems.add("Клиент оплачивает минимум один производственный комплект");
        }
        if (rates.getWeightReserveThresholdG() <= 0) {
            problems.add("Порог веса для запаса должен быть больше нуля");
        }
        if (rates.getEstimateFdmGramsPerHour() <= 0) {
            problems.add("Скорость FDM-печати для оценки должна быть больше нуля");
        }
        if (rates.getOfferValidityDays() < 1) {
            problems.add("Срок действия КП — минимум один день");
        }
        return problems;
    }

    private static void checkSteps(String name, List<RateStep> steps, boolean integerValues, List<String> problems) {
        if (steps.isEmpty()) {
            problems.add("Шкала " + name + " пуста");
            return;
        }
        int previous = 0;
        for (int i = 0; i < steps.size(); i++) {
            RateStep step = steps.get(i);
            if (step == null || step.from() == null || step.value() == null) {
                problems.add("Шкала " + name + ": ступень " + (i + 1) + " заполнена не полностью");
                return;
            }
            if (i == 0 && step.from() != 1) {
                problems.add("Шкала " + name + " должна начинаться с тиража 1");
            }
            if (step.from() <= previous) {
                problems.add("Шкала " + name + ": тиражи ступеней должны возрастать");
            }
            if (step.value() < 0 || Double.isNaN(step.value()) || Double.isInfinite(step.value())) {
                problems.add("Шкала " + name + ": значение ступени " + (i + 1) + " должно быть неотрицательным");
            }
            if (integerValues && (step.value() < 1 || step.value() != Math.rint(step.value()))) {
                problems.add("Шкала " + name + ": значение ступени " + (i + 1) + " — целое число от 1");
            }
            previous = step.from();
        }
    }

    private static Object read(Field field, CalculatorRates rates) {
        try {
            field.setAccessible(true);
            return field.get(rates);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Не удалось прочитать ставку " + field.getName(), e);
        }
    }
}
