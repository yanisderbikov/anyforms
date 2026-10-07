package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Справочник ставок калькулятора заказа")
public class CalculatorRates {

    private Double modelMarkup;
    private Double modelMinContractorPrice;
    private Double modelReserveShare;
    private Double modelFixedIncome;
    private Double taxRate;

    private Double slaRubPerMl;
    private Double slaRubPerHour;
    private Double slaFixedPerPrint;
    private Double slaShellMm;
    private Double slaInfillShare;
    private Double slaSupportsShare;

    private Double fdmProjectRubPerHour;
    private Double fdmRubPerGram;
    private Double fdmRubPerHour;
    private Double fdmFirstSlicingRub;

    private Double processingRubPerHour;
    private Double cncRubPerHour;
    private Double prepDefaultRub;

    private Double formRubPerGramPlatinum;
    private Double formRubPerGramTin;
    private Double shellRubPerGram;
    private Double shellPrintTimeShare;
    private Double pourRubPerCast;
    private Double cutMinutes;
    private Double cutRubPerHour;
    private Double weightReserveDefault;
    private Double weightReserveThresholdG;

    private Double tinRubPerGram;
    private Double tinWorkRubPerForm;
    private Double copyRubPerGram;
    private Double copyMinRub;
    private Double extraCopyPrepRub;
    private Integer maxKitsPaidByClient;

    private List<RateStep> kitsByTirage;
    private List<RateStep> minFormPriceByTirage;

    private Double kpRoundingStep;
    private Integer offerValidityDays;

    private Double fullCostLaborRubPerHour;
    private Double fullCostOverheadRubPerHour;
    private Double managerCommission;
    private Double minProfitShare;
    private Double targetProfitShareTypical;
    private Double costResinRubPerMl;
    private Double costPrintWashRub;
    private Double costPetgRubPerGram;
    private Double costPlatinumRubPerGram;
    private Double costTinRubPerGram;
    private Double costSiliconeWasteFactor;
    private Double costCopyRubPerGram;
    private Double costHoursPrint;
    private Double costHoursPrep;
    private Double costHoursPerTinForm;
    private Double costHoursPerKit;
    private Double costHoursPerForm;

    private Double estimateAreaShare;
    private Double estimateVolumeShare;
    private Double estimateLargeSizeMm;
    private Double estimateSiliconeDensity;
    private Double estimatePetgDensity;
    private Double estimateCopyDensity;
    private Double estimateStockingWallMm;
    private Double estimateStockingWallLargeMm;
    private Double estimateConvexity;
    private Double estimateFlangeCm3;
    private Double estimateCutWallMm;
    private Double estimateCutRibMm;
    private Double estimateBrickOffsetMm;
    private Double estimateBrickOffsetLargeMm;
    private Double estimateCupWallMm;
    private Double estimateReliefWallMm;
    private Double estimateReliefBaseFactor;
    private Double estimateMatrixWallMm;
    private Double estimateClusterWallMm;
    private Double estimateCompositeOffsetMm;
    private Double estimateCompositeLocksShare;
    private Double estimateShellHeightMm;
    private Double estimateShellWallMm;
    private Double estimateShellFactorStable;
    private Double estimateShellFactorUnstable;
    private Double estimateTinWallMm;
    private Double estimateTinConvexity;
    private Double estimateFdmGramsPerHour;
    private Double estimateSlaHoursPerMm;
    private Double estimateFdmProjectHours;
    private Double estimateProcessingHours;
}
