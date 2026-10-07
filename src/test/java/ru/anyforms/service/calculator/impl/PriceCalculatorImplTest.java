package ru.anyforms.service.calculator.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.CostBreakdown;
import ru.anyforms.dto.calculator.PriceBreakdown;
import ru.anyforms.dto.calculator.PriceInput;
import ru.anyforms.model.calculator.SiliconeType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceCalculatorImplTest {

    private static final double KOPECK = 0.005;

    private final CalculatorRates rates = SeedRates.load();
    private final PriceCalculatorImpl calculator = new PriceCalculatorImpl();

    static PriceInput.PriceInputBuilder onigiriStocking(int tirage) {
        return PriceInput.builder()
                .silicone(SiliconeType.PLATINUM)
                .tirage(tirage)
                .modelContractorPrice(3000)
                .slaAreaCm2(273)
                .slaVolumeCm3(127)
                .slaHours(8)
                .textureFactor(1)
                .fdmProjectHours(1)
                .kitGrams(100)
                .kitHours(4)
                .processingHours(1)
                .prepRub(1500)
                .needsIntermediate(true)
                .tinGrams(205)
                .tinFormsCount(1)
                .copyGrams(133)
                .siliconeGrams(146)
                .shellGrams(133)
                .weightReserve(0.20);
    }

    static PriceInput.PriceInputBuilder onigiriBrickWithCut(int tirage) {
        return onigiriStocking(tirage)
                .kitGrams(80)
                .kitHours(3)
                .siliconeGrams(291)
                .shellGrams(0)
                .hasCut(true);
    }

    @Test
    void case1OnigiriStockingPlatinumTirage5() {
        PriceBreakdown p = calculator.price(onigiriStocking(5).build(), rates);

        assertEquals(6600.00, p.model(), KOPECK);
        assertEquals(69.45, p.slaMl(), KOPECK);
        assertEquals(2809.02, p.sla(), KOPECK);
        assertEquals(2060.00, p.kit(), KOPECK);
        assertEquals(1347.00, p.tin(), KOPECK);
        assertEquals(500.00, p.copy(), KOPECK);
        assertEquals(1, p.kits());
        assertEquals(0, p.extraKits(), KOPECK);
        assertEquals(17566.02, p.development(), KOPECK);
        assertEquals(175.20, p.siliconeWeight(), KOPECK);
        assertEquals(946.08, p.siliconeCost(), KOPECK);
        assertEquals(1064.00, p.shellCost(), KOPECK);
        assertEquals(57.00, p.shellPrint(), KOPECK);
        assertEquals(2107.08, p.formCalc(), KOPECK);
        assertEquals(2107.08, p.formPrice(), KOPECK);
        assertFalse(p.minPriceApplied());
        assertEquals(10535.40, p.formsTotal(), KOPECK);
        assertFalse(p.cliffApplied());
        assertEquals(28101.42, p.total(), KOPECK);
    }

    @Test
    void case2OnigiriBrickWithCutTirage5() {
        PriceBreakdown p = calculator.price(onigiriBrickWithCut(5).build(), rates);

        assertEquals(17311.02, p.development(), KOPECK);
        assertEquals(56.25, p.cut(), KOPECK);
        assertEquals(1981.93, p.formPrice(), KOPECK);
        assertEquals(27220.67, p.total(), KOPECK);
    }

    @Test
    void case3OnigiriBrickWithCutTirage10AddsSecondKit() {
        PriceBreakdown p = calculator.price(onigiriBrickWithCut(10).build(), rates);

        assertEquals(2, p.kits());
        assertEquals(2, p.kitsPaid());
        assertEquals(1805.00, p.extraKits(), KOPECK);
        assertEquals(19116.02, p.development(), KOPECK);
        assertEquals(1981.93, p.formPrice(), KOPECK);
        assertEquals(38935.32, p.total(), KOPECK);
    }

    @Test
    void case4SachetMatrixSixteenCellsTirage1() {
        PriceInput input = PriceInput.builder()
                .silicone(SiliconeType.PLATINUM)
                .tirage(1)
                .modelContractorPrice(0)
                .slaAreaCm2(110)
                .slaVolumeCm3(35)
                .slaHours(2)
                .textureFactor(1)
                .fdmProjectHours(3)
                .kitGrams(425)
                .kitHours(14.8)
                .processingHours(2.25)
                .prepRub(3000)
                .needsIntermediate(true)
                .tinGrams(120)
                .tinFormsCount(1)
                .copyGrams(1049)
                .siliconeGrams(622)
                .shellGrams(0)
                .weightReserve(0.20)
                .build();

        PriceBreakdown p = calculator.price(input, rates);

        assertEquals(0, p.model(), KOPECK);
        assertEquals(22836.42, p.development(), KOPECK);
        assertEquals(4070.56, p.formPrice(), KOPECK);
        assertEquals(26906.98, p.total(), KOPECK);
    }

    @Test
    void onigiriTirage10MatchesWorkedExample() {
        PriceBreakdown p = calculator.price(onigiriStocking(10).build(), rates);

        assertEquals(2060.00, p.extraKits(), KOPECK);
        assertEquals(19626.02, p.development(), KOPECK);
        assertEquals(21070.80, p.formsTotal(), KOPECK);
        assertEquals(40696.82, p.total(), KOPECK);
    }

    @Test
    void cliffProtectionKeepsSixFormsNotCheaperThanFive() {
        PriceInput input = PriceInput.builder()
                .silicone(SiliconeType.PLATINUM)
                .tirage(6)
                .textureFactor(1)
                .extraPerFormRub(860)
                .weightReserve(0.20)
                .tinFormsCount(1)
                .build();

        PriceBreakdown p = calculator.price(input, rates);

        assertEquals(900.00, p.formCalc(), KOPECK);
        assertEquals(950.00, p.formPrice(), KOPECK);
        assertTrue(p.minPriceApplied());
        assertTrue(p.cliffApplied());
        assertEquals(6000.00, p.formsTotal(), KOPECK);
    }

    @Test
    void modelPriceHasCeilingForExpensiveContractors() {
        PriceBreakdown expensive = calculator.price(onigiriStocking(5).modelContractorPrice(8000).build(), rates);
        PriceBreakdown cheap = calculator.price(onigiriStocking(5).modelContractorPrice(2000).build(), rates);
        PriceBreakdown mid = calculator.price(onigiriStocking(5).modelContractorPrice(5000).build(), rates);

        assertEquals(15531.91, expensive.model(), KOPECK);
        assertEquals(6600.00, cheap.model(), KOPECK);
        assertEquals(11000.00, mid.model(), KOPECK);
    }

    @Test
    void clientModelAndOverridesFollowRegulation() {
        assertEquals(0, calculator.price(onigiriStocking(5).hasClientModel(true).build(), rates).model(), KOPECK);
        assertEquals(8000, calculator.price(onigiriStocking(5).modelPriceOverride(8000.0).build(), rates).model(), KOPECK);

        PriceBreakdown oneKit = calculator.price(onigiriStocking(30).kitsOverride(1).build(), rates);
        assertEquals(1, oneKit.kits());
        assertEquals(0, oneKit.extraKits(), KOPECK);

        PriceBreakdown manual = calculator.price(onigiriStocking(6).formPriceOverride(500.0).build(), rates);
        assertEquals(500, manual.formPrice(), KOPECK);
        assertEquals(3000, manual.formsTotal(), KOPECK);
        assertFalse(manual.cliffApplied());
    }

    @Test
    void clientPaysAtMostThreeKits() {
        PriceBreakdown p = calculator.price(onigiriStocking(150).build(), rates);

        assertEquals(10, p.kits());
        assertEquals(3, p.kitsPaid());
        assertEquals(2 * 2060.00, p.extraKits(), KOPECK);
    }

    @Test
    void tinSiliconeNeedsNoIntermediateForm() {
        PriceBreakdown p = calculator.price(onigiriStocking(10).silicone(SiliconeType.TIN).needsIntermediate(false).build(), rates);

        assertEquals(0, p.tin(), KOPECK);
        assertEquals(0, p.copy(), KOPECK);
        assertEquals(1, p.kits());
        assertEquals(17566.02 - 1347 - 500, p.development(), KOPECK);
    }

    @Test
    void digitalProductIsModelPlusProject() {
        PriceBreakdown p = calculator.price(onigiriStocking(5).digitalOnly(true).build(), rates);

        assertEquals(6600 + 1250, p.development(), KOPECK);
        assertEquals(0, p.formsTotal(), KOPECK);
        assertEquals(7850, p.total(), KOPECK);
    }

    @Test
    void heavyFormHasNoWeightReserve() {
        PriceBreakdown p = calculator.price(onigiriStocking(5).siliconeGrams(1200).build(), rates);
        assertEquals(1200, p.siliconeWeight(), KOPECK);

        PriceBreakdown capped = calculator.price(onigiriStocking(5).siliconeGrams(900).build(), rates);
        assertEquals(1000, capped.siliconeWeight(), KOPECK);
    }

    @Test
    void fullCostFollowsProfitabilitySection() {
        PriceInput input = onigiriStocking(5).build();
        CostBreakdown cost = calculator.cost(input, calculator.price(input, rates), rates);

        assertEquals(3000, cost.contractor(), KOPECK);
        assertEquals(69.4508 * 2.5 + 150, cost.resin(), KOPECK);
        assertEquals(85, cost.kitPlastic(), KOPECK);
        assertEquals(205 * 0.827 * 1.1 + 133 * 1.2, cost.intermediate(), KOPECK);
        assertEquals(5 * (146 * 1.1 * 0.68 + 133 * 0.85), cost.forms(), KOPECK);
        assertEquals(1 + 1 + 1 + 1.5 + 2 + 0.5 + 2.5, cost.hours(), KOPECK);
        assertEquals(9.5 * 1004, cost.labor(), KOPECK);
    }
}
