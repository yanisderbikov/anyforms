package ru.anyforms.service.calculator.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.calculator.CalculationDiscountRequest;
import ru.anyforms.dto.calculator.CalculationHint;
import ru.anyforms.dto.calculator.CalculationOption;
import ru.anyforms.dto.calculator.CalculationPositionRequest;
import ru.anyforms.dto.calculator.CalculationVariantRequest;
import ru.anyforms.dto.calculator.OrderCalculationRequest;
import ru.anyforms.dto.calculator.OrderCalculationResult;
import ru.anyforms.dto.calculator.ParameterSource;
import ru.anyforms.dto.calculator.PositionCalculation;
import ru.anyforms.model.Role;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.model.calculator.PourMaterial;
import ru.anyforms.model.calculator.SiliconeType;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.calculator.CalculatorAiService;
import ru.anyforms.service.calculator.CalculatorRatesService;
import ru.anyforms.service.calculator.RatesSnapshot;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderCalculatorServiceImplTest {

    private static final double KOPECK = 0.005;
    private static final UserAccess MANAGER = new UserAccess("manager@anyforms.ru", "Менеджер", Role.SALES_MANAGER, false, null, null);
    private static final UserAccess FOUNDER = new UserAccess("founder@anyforms.ru", "Основатель", Role.ADMIN, false, null, null);

    private final CalculatorRatesService ratesService = mock(CalculatorRatesService.class);
    private final CalculatorAiService aiService = mock(CalculatorAiService.class);
    private final OrderCalculatorServiceImpl service = new OrderCalculatorServiceImpl(
            new PriceCalculatorImpl(), new FormulaParameterEstimator(), ratesService, aiService);

    @BeforeEach
    void setUp() {
        when(ratesService.current()).thenReturn(new RatesSnapshot(1L, SeedRates.load(), Instant.now(), "seed"));
    }

    private static CalculationVariantRequest.CalculationVariantRequestBuilder onigiriStocking() {
        return CalculationVariantRequest.builder()
                .formType(FormType.STOCKING)
                .modelContractorPrice(3000.0)
                .slaAreaCm2(273.0)
                .slaVolumeCm3(127.0)
                .slaHours(8.0)
                .fdmProjectHours(1.0)
                .kitGrams(100.0)
                .kitHours(4.0)
                .processingHours(1.0)
                .prepRub(1500.0)
                .tinGrams(205.0)
                .copyGrams(133.0)
                .siliconeGrams(146.0)
                .shellGrams(133.0);
    }

    private static CalculationPositionRequest.CalculationPositionRequestBuilder onigiri(List<Integer> tirages) {
        return CalculationPositionRequest.builder()
                .productName("Свеча Онигири")
                .widthMm(80.0)
                .depthMm(45.0)
                .heightMm(80.0)
                .pourMaterial(PourMaterial.WAX)
                .tirages(tirages)
                .silicones(List.of(SiliconeType.PLATINUM))
                .variants(List.of(onigiriStocking().build()));
    }

    private static OrderCalculationRequest order(CalculationPositionRequest... positions) {
        return OrderCalculationRequest.builder().client("Анкор ЖБИ").positions(List.of(positions)).build();
    }

    @Test
    void onigiriOfferMatchesRegulationForBothTirages() {
        OrderCalculationResult result = service.calculate(order(onigiri(List.of(5, 10)).build()), MANAGER);

        PositionCalculation position = result.positions().get(0);
        assertEquals(2, position.options().size());
        CalculationOption five = position.options().get(0);
        CalculationOption ten = position.options().get(1);

        assertEquals(28101.42, five.price().total(), KOPECK);
        assertEquals(17570, five.kp().development(), KOPECK);
        assertEquals(2110, five.kp().formPrice(), KOPECK);
        assertEquals(28120, five.kp().total(), KOPECK);
        assertEquals(40730, ten.kp().total(), KOPECK);
        assertEquals(19630, ten.kp().development(), KOPECK);

        assertEquals(0, position.selectedOption());
        assertEquals(28120, result.summary().totalKp(), KOPECK);
        assertEquals(28120, result.summary().totalOffer(), KOPECK);
        assertFalse(result.preliminary());
        assertFalse(result.hasEstimates());
        assertEquals(ParameterSource.DEFAULT, five.sources().get("needsIntermediate"));
        assertTrue(five.input().needsIntermediate());
        assertFalse(ten.hints().stream().anyMatch(h -> h.code().equals("KITS_STEP")));
    }

    @Test
    void selectedTirageGoesToOrderTotal() {
        OrderCalculationResult result = service.calculate(order(onigiri(List.of(5, 10)).selectedTirage(10).build()), MANAGER);

        assertEquals(1, result.positions().get(0).selectedOption());
        assertEquals(40730, result.summary().totalOffer(), KOPECK);
    }

    @Test
    void kitsStepIsHintedOnSixthForm() {
        OrderCalculationResult result = service.calculate(order(onigiri(List.of(6)).build()), MANAGER);

        assertTrue(result.positions().get(0).options().get(0).hints().stream()
                .anyMatch(h -> h.code().equals("KITS_STEP")));
    }

    @Test
    void marginAndMaxDiscountAreReported() {
        OrderCalculationResult result = service.calculate(order(onigiri(List.of(5)).build()), MANAGER);

        var s = result.summary();
        double expectedProfit = 28120 * 0.9 - s.cost();
        assertEquals(expectedProfit, s.profit(), 0.01);
        assertEquals(expectedProfit / 28120, s.margin(), 0.0001);
        assertEquals(s.cost() / 0.65, s.minPrice(), 0.01);
        assertEquals(28120 - s.minPrice(), s.maxDiscountRub(), 0.01);
        assertFalse(s.belowMinMargin());
    }

    @Test
    void excessiveFormsDiscountIsCappedToMaximum() {
        CalculationDiscountRequest discount = CalculationDiscountRequest.builder().formsPercent(60.0).build();
        OrderCalculationResult result = service.calculate(
                order(onigiri(List.of(5)).build()).toBuilder().discount(discount).build(), MANAGER);

        var s = result.summary();
        assertTrue(s.formsDiscountCapped());
        assertTrue(s.formsDiscountApplied() < 60);
        assertTrue(s.margin() >= 0.25);
        assertFalse(s.belowMinMargin());
        assertTrue(result.hints().stream().anyMatch(h -> h.code().equals("FORMS_DISCOUNT_CAPPED")));

        CalculationDiscountRequest onePercentMore = CalculationDiscountRequest.builder()
                .formsPercent(s.formsDiscountApplied() + 1)
                .allowBelowMinMargin(true)
                .build();
        OrderCalculationResult tooMuch = service.calculate(
                order(onigiri(List.of(5)).build()).toBuilder().discount(onePercentMore).build(), FOUNDER);
        assertTrue(tooMuch.summary().belowMinMargin());
    }

    @Test
    void smallDiscountIsAppliedToFormPriceAndRoundedToTens() {
        CalculationDiscountRequest discount = CalculationDiscountRequest.builder().formsPercent(5.0).build();
        OrderCalculationResult result = service.calculate(
                order(onigiri(List.of(5)).build()).toBuilder().discount(discount).build(), MANAGER);

        CalculationOption option = result.positions().get(0).options().get(0);
        assertEquals(2000, option.offer().formPrice(), KOPECK);
        assertEquals(17570 + 2000 * 5, option.offer().total(), KOPECK);
        assertFalse(result.summary().formsDiscountCapped());
        assertEquals(28120 - 27570, result.summary().discountRub(), KOPECK);
    }

    @Test
    void founderMayGoBelowMinimumMargin() {
        CalculationDiscountRequest discount = CalculationDiscountRequest.builder()
                .formsPercent(60.0)
                .allowBelowMinMargin(true)
                .comment("повторный клиент")
                .build();
        OrderCalculationResult result = service.calculate(
                order(onigiri(List.of(5)).build()).toBuilder().discount(discount).build(), FOUNDER);

        assertEquals(60, result.summary().formsDiscountApplied(), KOPECK);
        assertTrue(result.summary().belowMinMargin());
        assertTrue(result.hints().stream().anyMatch(h -> h.code().equals("BELOW_MIN_MARGIN_APPROVED")));
    }

    @Test
    void managerCannotUseFounderExceptions() {
        CalculationPositionRequest withOverride = onigiri(List.of(5))
                .variants(List.of(onigiriStocking().formPriceOverride(950.0).build()))
                .build();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.calculate(order(withOverride), MANAGER));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());

        OrderCalculationResult result = service.calculate(order(withOverride), FOUNDER);
        assertTrue(result.hasExceptions());
        assertTrue(result.hints().stream().anyMatch(h -> h.code().equals("EXCEPTION_COMMENT")));
    }

    @Test
    void missingTechnicalFieldsAreEstimatedAndFlagged() {
        CalculationPositionRequest sparse = CalculationPositionRequest.builder()
                .productName("Гном")
                .widthMm(80.0)
                .depthMm(45.0)
                .heightMm(80.0)
                .pourMaterial(PourMaterial.GYPSUM)
                .tirages(List.of(5))
                .variants(List.of(CalculationVariantRequest.builder().formType(FormType.STOCKING)
                        .modelContractorPrice(4000.0).build()))
                .build();

        OrderCalculationResult result = service.calculate(order(sparse), MANAGER);
        CalculationOption option = result.positions().get(0).options().get(0);

        assertTrue(result.hasEstimates());
        assertTrue(result.preliminary());
        assertEquals(ParameterSource.ESTIMATE, option.sources().get("siliconeGrams"));
        assertNull(option.sources().get("modelContractorPrice"));
        assertEquals(ParameterSource.DEFAULT, option.sources().get("prepRub"));
        assertEquals(8.0, option.input().slaHours(), KOPECK);
        assertEquals(4000, option.input().modelContractorPrice(), KOPECK);
        assertTrue(option.input().siliconeGrams() > 0);
        assertTrue(option.input().shellGrams() > 0);
        assertTrue(option.input().copyGrams() > 0);
        assertNotNull(result.summary().margin());
        assertTrue(result.hints().stream().anyMatch(h -> h.code().equals("PRELIMINARY")));
    }

    @Test
    void noDimensionsMeansNoWeightEstimate() {
        CalculationPositionRequest empty = CalculationPositionRequest.builder()
                .productName("Неизвестно")
                .variants(List.of(CalculationVariantRequest.builder().formType(FormType.BRICK)
                        .modelContractorPrice(3000.0).build()))
                .build();

        OrderCalculationResult result = service.calculate(order(empty), MANAGER);
        PositionCalculation position = result.positions().get(0);

        assertTrue(position.preliminary());
        assertEquals(1, position.options().get(0).tirage());
        assertTrue(position.hints().stream().anyMatch(h -> h.code().equals("NO_GEOMETRY")));
        assertTrue(position.hints().stream().anyMatch(h -> h.code().equals("NO_TIRAGE")));
    }

    @Test
    void foodProductWarnsAboutTin() {
        CalculationPositionRequest food = onigiri(List.of(5))
                .pourMaterial(PourMaterial.FOOD)
                .silicones(List.of(SiliconeType.PLATINUM, SiliconeType.TIN))
                .build();

        OrderCalculationResult result = service.calculate(order(food), MANAGER);
        PositionCalculation position = result.positions().get(0);

        assertEquals(2, position.options().size());
        assertTrue(position.hints().stream()
                .anyMatch(h -> h.code().equals("PLATINUM_ONLY") && h.level() == CalculationHint.Level.ERROR));
        CalculationOption tin = position.options().get(1);
        assertEquals(SiliconeType.TIN, tin.silicone());
        assertTrue(tin.hints().stream().anyMatch(h -> h.code().equals("PLATINUM_ONLY_OPTION")));
        assertTrue(position.options().get(0).hints().stream().noneMatch(h -> h.code().equals("PLATINUM_ONLY_OPTION")));
        assertFalse(tin.input().needsIntermediate());
        assertTrue(tin.kp().development() < position.options().get(0).kp().development());
    }

    @Test
    void bonusPositionIsFreeButCostsCount() {
        CalculationPositionRequest main = onigiri(List.of(5)).build();
        CalculationPositionRequest bonus = onigiri(List.of(5)).productName("Бирка").bonus(true)
                .exceptionComment("в подарок").build();

        OrderCalculationResult single = service.calculate(order(main), FOUNDER);
        OrderCalculationResult withBonus = service.calculate(order(main, bonus), FOUNDER);

        assertEquals(single.summary().totalOffer(), withBonus.summary().totalOffer(), KOPECK);
        assertEquals(single.summary().cost() * 2, withBonus.summary().cost(), 0.02);
        assertEquals(0, withBonus.positions().get(1).selected().offer().total(), KOPECK);
        assertTrue(withBonus.positions().get(1).selected().kp().total() > 0);
    }

    @Test
    void sharedModelAndPrintAreCountedOnce() {
        CalculationPositionRequest first = onigiri(List.of(5)).build();
        CalculationPositionRequest second = onigiri(List.of(5)).productName("Онигири мини")
                .sharedModel(true).sharedSlaPrint(true).build();

        OrderCalculationResult result = service.calculate(order(first, second), MANAGER);
        CalculationOption shared = result.positions().get(1).selected();

        assertEquals(0, shared.price().model(), KOPECK);
        assertEquals(0, shared.price().sla(), KOPECK);
        assertEquals(0, shared.cost().contractor(), KOPECK);
        assertEquals(17566.02 - 6600 - 2809.02, shared.price().development(), KOPECK);
    }

    @Test
    void digitalProductCollapsesVariants() {
        CalculationPositionRequest digital = onigiri(List.of(5, 10))
                .silicones(List.of(SiliconeType.PLATINUM, SiliconeType.TIN))
                .digitalOnly(true)
                .build();

        OrderCalculationResult result = service.calculate(order(digital), MANAGER);
        PositionCalculation position = result.positions().get(0);

        assertEquals(1, position.options().size());
        assertEquals(6600 + 1250, position.selected().price().development(), KOPECK);
        assertEquals(7850, result.summary().totalOffer(), KOPECK);
    }

    @Test
    void optionsExposeFounderFlagAndAiAvailability() {
        when(aiService.available()).thenReturn(false);

        assertTrue(service.options(FOUNDER).founder());
        assertFalse(service.options(MANAGER).founder());
        assertFalse(service.options(MANAGER).aiAvailable());
        assertEquals(FormType.values().length, service.options(MANAGER).formTypes().size());
    }

    @Test
    void aiEstimatedGeometryKeepsPreliminaryOffer() {
        CalculationVariantRequest aiGuess = CalculationVariantRequest.builder()
                .formType(FormType.STOCKING)
                .modelContractorPrice(3000.0)
                .slaAreaCm2(273.0)
                .slaVolumeCm3(127.0)
                .aiFields(List.of("slaAreaCm2", "slaVolumeCm3"))
                .build();
        CalculationPositionRequest guessed = onigiri(List.of(5)).variants(List.of(aiGuess)).build();
        CalculationPositionRequest measured = onigiri(List.of(5))
                .variants(List.of(aiGuess.toBuilder().aiFields(List.of()).build()))
                .build();

        OrderCalculationResult byAi = service.calculate(order(guessed), MANAGER);
        OrderCalculationResult byModel = service.calculate(order(measured), MANAGER);

        assertTrue(byAi.preliminary());
        assertEquals(ParameterSource.AI, byAi.positions().get(0).options().get(0).sources().get("slaAreaCm2"));
        assertFalse(byModel.preliminary());
    }

    @Test
    void modelContractorPriceIsRequired() {
        CalculationVariantRequest noPrice = onigiriStocking().modelContractorPrice(null).build();
        CalculationPositionRequest first = onigiri(List.of(5)).build();
        CalculationPositionRequest second = onigiri(List.of(5)).productName("Гном")
                .variants(List.of(onigiriStocking().build(), noPrice))
                .build();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.calculate(order(first, second), MANAGER));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Укажите цену художника за модель: позиция 2 «Гном», вариант 2", e.getReason());
    }

    @Test
    void modelPriceIsNotNeededForClientOrSharedModel() {
        CalculationVariantRequest noPrice = onigiriStocking().modelContractorPrice(null).build();
        CalculationPositionRequest clientModel = onigiri(List.of(5)).hasClientModel(true)
                .variants(List.of(noPrice)).build();
        CalculationPositionRequest shared = onigiri(List.of(5)).sharedModel(true)
                .variants(List.of(noPrice)).build();

        OrderCalculationResult result = service.calculate(order(clientModel, shared), MANAGER);

        assertEquals(0, result.positions().get(0).options().get(0).price().model(), KOPECK);
        assertEquals(0, result.positions().get(1).options().get(0).price().model(), KOPECK);
        assertEquals(0, result.positions().get(0).options().get(0).cost().contractor(), KOPECK);
    }
}
