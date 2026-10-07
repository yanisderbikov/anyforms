package ru.anyforms.service.calculator.impl;

import org.springframework.stereotype.Component;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.CostBreakdown;
import ru.anyforms.dto.calculator.PriceBreakdown;
import ru.anyforms.dto.calculator.PriceInput;
import ru.anyforms.dto.calculator.RateStep;
import ru.anyforms.model.calculator.SiliconeType;
import ru.anyforms.service.calculator.PriceCalculator;

import java.util.List;

import static ru.anyforms.service.calculator.impl.CalculatorMath.rub;
import static ru.anyforms.service.calculator.impl.CalculatorMath.stepIndex;
import static ru.anyforms.service.calculator.impl.CalculatorMath.stepValue;

@Component
class PriceCalculatorImpl implements PriceCalculator {

    @Override
    public PriceBreakdown price(PriceInput in, CalculatorRates r) {
        double model = model(in, r);
        double fdmProject = in.fdmProjectHours() * r.getFdmProjectRubPerHour();

        if (in.digitalOnly()) {
            double development = model + fdmProject;
            return PriceBreakdown.builder()
                    .model(rub(model))
                    .fdmProject(rub(fdmProject))
                    .development(rub(development))
                    .total(rub(development))
                    .build();
        }

        double ml = in.sharedSlaPrint() ? 0 : resinMl(in, r);
        double sla = in.sharedSlaPrint() ? 0
                : ml * r.getSlaRubPerMl() + in.slaHours() * r.getSlaRubPerHour() + (ml > 0 ? r.getSlaFixedPerPrint() : 0);

        double kitReprint = in.kitGrams() * r.getFdmRubPerGram() + in.kitHours() * r.getFdmRubPerHour();
        double kit = kitReprint + (in.kitGrams() > 0 ? r.getFdmFirstSlicingRub() : 0);
        double processing = in.processingHours() * r.getProcessingRubPerHour();
        double cnc = in.cncHours() * r.getCncRubPerHour();
        double prep = in.prepRub();

        double tin = 0;
        double copy = 0;
        int kits;
        if (in.needsIntermediate()) {
            tin = in.tinGrams() * r.getTinRubPerGram() + r.getTinWorkRubPerForm() * Math.max(in.tinFormsCount(), 1);
            copy = Math.max(in.copyGrams() * r.getCopyRubPerGram(), r.getCopyMinRub());
            kits = in.kitsOverride() != null ? in.kitsOverride() : (int) Math.round(stepValue(r.getKitsByTirage(), in.tirage()));
        } else {
            kits = in.kitsOverride() != null ? in.kitsOverride() : 1;
        }
        int kitsPaid = Math.min(kits, r.getMaxKitsPaidByClient());
        double extraKits = in.needsIntermediate()
                ? Math.max(kitsPaid - 1, 0) * (kitReprint + copy + r.getExtraCopyPrepRub())
                : 0;

        double development = model + sla + fdmProject + kit + processing + cnc + prep + tin + copy + extraKits;

        double threshold = r.getWeightReserveThresholdG();
        double weight = in.siliconeGrams() < threshold
                ? Math.min(in.siliconeGrams() * (1 + in.weightReserve()), threshold)
                : in.siliconeGrams();
        double rate = in.silicone() == SiliconeType.TIN ? r.getFormRubPerGramTin() : r.getFormRubPerGramPlatinum();
        double siliconeCost = weight * rate;
        double shellCost = in.shellGrams() * r.getShellRubPerGram();
        double shellPrint = in.shellGrams() > 0 ? in.kitHours() * r.getShellPrintTimeShare() * r.getFdmRubPerHour() : 0;
        double pour = r.getPourRubPerCast();
        double cut = in.hasCut() ? r.getCutMinutes() / 60 * r.getCutRubPerHour() : 0;
        double extra = in.extraPerFormRub();
        double formCalc = siliconeCost + shellCost + shellPrint + pour + cut + extra;

        List<RateStep> minPrices = r.getMinFormPriceByTirage();
        double minFormPrice = stepValue(minPrices, in.tirage());
        boolean overridden = in.formPriceOverride() != null;
        double formPrice = overridden ? in.formPriceOverride() : Math.max(formCalc, minFormPrice);

        double formsTotal = in.tirage() * formPrice;
        int index = stepIndex(minPrices, in.tirage());
        double cliffFloor = 0;
        boolean cliffApplied = false;
        if (index > 0 && !overridden) {
            double previousMin = minPrices.get(index - 1).value();
            int currentStart = minPrices.get(index).from();
            cliffFloor = (currentStart - 1) * Math.max(formCalc, previousMin);
            if (cliffFloor > formsTotal) {
                formsTotal = cliffFloor;
                cliffApplied = true;
            }
        }

        double total = development + formsTotal;

        return PriceBreakdown.builder()
                .model(rub(model))
                .slaMl(rub(ml))
                .sla(rub(sla))
                .fdmProject(rub(fdmProject))
                .kit(rub(kit))
                .processing(rub(processing))
                .cnc(rub(cnc))
                .prep(rub(prep))
                .tin(rub(tin))
                .copy(rub(copy))
                .kits(kits)
                .kitsPaid(kitsPaid)
                .extraKits(rub(extraKits))
                .development(rub(development))
                .siliconeWeight(rub(weight))
                .siliconeCost(rub(siliconeCost))
                .shellCost(rub(shellCost))
                .shellPrint(rub(shellPrint))
                .pour(rub(pour))
                .cut(rub(cut))
                .extra(rub(extra))
                .formCalc(rub(formCalc))
                .minFormPrice(rub(minFormPrice))
                .formPrice(rub(formPrice))
                .minPriceApplied(!overridden && minFormPrice > formCalc)
                .formPriceOverridden(overridden)
                .formsTotal(rub(formsTotal))
                .cliffApplied(cliffApplied)
                .cliffFloor(rub(cliffFloor))
                .total(rub(total))
                .build();
    }

    @Override
    public CostBreakdown cost(PriceInput in, PriceBreakdown price, CalculatorRates r) {
        double hourCost = r.getFullCostLaborRubPerHour() + r.getFullCostOverheadRubPerHour();
        double contractor = in.hasClientModel() || in.sharedModel() ? 0 : in.modelContractorPrice();

        if (in.digitalOnly()) {
            double hours = in.fdmProjectHours();
            double labor = hours * hourCost;
            return new CostBreakdown(rub(contractor), 0, 0, 0, 0, rub(hours), rub(labor), rub(contractor + labor));
        }

        int prints = price.slaMl() > 0 ? 1 : 0;
        int kits = price.kits();
        double resin = price.slaMl() * r.getCostResinRubPerMl() + r.getCostPrintWashRub() * prints;
        double kitPlastic = in.kitGrams() * r.getCostPetgRubPerGram() * kits;
        double intermediate = in.needsIntermediate()
                ? in.tinGrams() * r.getCostTinRubPerGram() * r.getCostSiliconeWasteFactor()
                + in.copyGrams() * r.getCostCopyRubPerGram() * kits
                : 0;
        double siliconePrice = in.silicone() == SiliconeType.TIN ? r.getCostTinRubPerGram() : r.getCostPlatinumRubPerGram();
        double forms = in.tirage() * (in.siliconeGrams() * r.getCostSiliconeWasteFactor() * siliconePrice
                + in.shellGrams() * r.getCostPetgRubPerGram());
        double hours = in.fdmProjectHours() + in.processingHours()
                + r.getCostHoursPrint() * prints
                + r.getCostHoursPrep()
                + (in.needsIntermediate() ? r.getCostHoursPerTinForm() * Math.max(in.tinFormsCount(), 1) : 0)
                + r.getCostHoursPerKit() * kits
                + r.getCostHoursPerForm() * in.tirage();
        double labor = hours * hourCost;
        double total = contractor + resin + kitPlastic + intermediate + forms + labor;
        return new CostBreakdown(rub(contractor), rub(resin), rub(kitPlastic), rub(intermediate), rub(forms),
                rub(hours), rub(labor), rub(total));
    }

    private double model(PriceInput in, CalculatorRates r) {
        if (in.hasClientModel() || in.sharedModel()) {
            return 0;
        }
        if (in.modelPriceOverride() != null) {
            return in.modelPriceOverride();
        }
        if (in.modelContractorPrice() <= 0) {
            return 0;
        }
        double contractor = Math.max(in.modelContractorPrice(), r.getModelMinContractorPrice());
        double byMarkup = contractor * r.getModelMarkup();
        double byCeiling = (contractor * (1 + r.getModelReserveShare()) + r.getModelFixedIncome()) / (1 - r.getTaxRate());
        return Math.min(byMarkup, byCeiling);
    }

    private double resinMl(PriceInput in, CalculatorRates r) {
        if (in.slaAreaCm2() > 0) {
            double area = in.slaAreaCm2() * in.textureFactor();
            double shell = area * r.getSlaShellMm() / 10;
            double inner = Math.max(in.slaVolumeCm3() - shell, 0);
            return (shell + inner * r.getSlaInfillShare()) * (1 + r.getSlaSupportsShare());
        }
        return in.slaMlManual();
    }
}
