package ru.anyforms.service.calculator.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.RateStep;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorRatesSeedTest {

    @Test
    void seedHasEveryRateAndPassesValidation() {
        CalculatorRates rates = SeedRates.load();

        assertEquals(List.of(), CalculatorRatesValidator.validate(rates));
        assertEquals(2.2, rates.getModelMarkup());
        assertEquals(1300, rates.getSlaFixedPerPrint());
        assertEquals(new RateStep(150, 10.0), rates.getKitsByTirage().get(3));
        assertEquals(new RateStep(175, 0.0), rates.getMinFormPriceByTirage().get(4));
    }

    @Test
    void validationRejectsMissingRateAndBrokenScale() {
        CalculatorRates rates = SeedRates.load().toBuilder()
                .taxRate(null)
                .build();
        assertTrue(CalculatorRatesValidator.validate(rates).contains("Не заполнена ставка taxRate"));

        CalculatorRates unsorted = SeedRates.load().toBuilder()
                .minFormPriceByTirage(List.of(new RateStep(1, 1200.0), new RateStep(1, 950.0)))
                .build();
        assertFalse(CalculatorRatesValidator.validate(unsorted).isEmpty());

        CalculatorRates noMargin = SeedRates.load().toBuilder()
                .minProfitShare(0.95)
                .build();
        assertFalse(CalculatorRatesValidator.validate(noMargin).isEmpty());
    }
}
