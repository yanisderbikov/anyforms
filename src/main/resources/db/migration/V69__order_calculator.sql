CREATE TABLE calculator_rates (
    id               BIGSERIAL PRIMARY KEY,
    rates            TEXT                        NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by_email VARCHAR(255),
    created_by_name  VARCHAR(255)
);

INSERT INTO calculator_rates (rates, created_at, created_by_email, created_by_name)
VALUES ($rates$ {
  "modelMarkup": 2.2,
  "modelMinContractorPrice": 3000,
  "modelReserveShare": 0.20,
  "modelFixedIncome": 5000,
  "taxRate": 0.06,

  "slaRubPerMl": 20,
  "slaRubPerHour": 15,
  "slaFixedPerPrint": 1300,
  "slaShellMm": 2,
  "slaInfillShare": 0.08,
  "slaSupportsShare": 0.15,

  "fdmProjectRubPerHour": 1250,
  "fdmRubPerGram": 12,
  "fdmRubPerHour": 15,
  "fdmFirstSlicingRub": 800,

  "processingRubPerHour": 1500,
  "cncRubPerHour": 1200,
  "prepDefaultRub": 1500,

  "formRubPerGramPlatinum": 5.4,
  "formRubPerGramTin": 5.4,
  "shellRubPerGram": 8,
  "shellPrintTimeShare": 0.95,
  "pourRubPerCast": 40,
  "cutMinutes": 3,
  "cutRubPerHour": 1125,
  "weightReserveDefault": 0.20,
  "weightReserveThresholdG": 1000,

  "tinRubPerGram": 3.4,
  "tinWorkRubPerForm": 650,
  "copyRubPerGram": 3.5,
  "copyMinRub": 500,
  "extraCopyPrepRub": 300,
  "maxKitsPaidByClient": 3,

  "kitsByTirage": [
    {"from": 1, "value": 1},
    {"from": 6, "value": 2},
    {"from": 21, "value": 3},
    {"from": 150, "value": 10}
  ],
  "minFormPriceByTirage": [
    {"from": 1, "value": 1200},
    {"from": 6, "value": 950},
    {"from": 26, "value": 750},
    {"from": 50, "value": 500},
    {"from": 175, "value": 0}
  ],

  "kpRoundingStep": 10,
  "offerValidityDays": 14,

  "fullCostLaborRubPerHour": 600,
  "fullCostOverheadRubPerHour": 404,
  "managerCommission": 0.04,
  "minProfitShare": 0.25,
  "targetProfitShareTypical": 0.40,
  "costResinRubPerMl": 2.5,
  "costPrintWashRub": 150,
  "costPetgRubPerGram": 0.85,
  "costPlatinumRubPerGram": 0.68,
  "costTinRubPerGram": 0.827,
  "costSiliconeWasteFactor": 1.1,
  "costCopyRubPerGram": 1.2,
  "costHoursPrint": 1,
  "costHoursPrep": 1.5,
  "costHoursPerTinForm": 2,
  "costHoursPerKit": 0.5,
  "costHoursPerForm": 0.5,

  "estimateAreaShare": 0.8,
  "estimateVolumeShare": 0.45,
  "estimateLargeSizeMm": 150,
  "estimateSiliconeDensity": 1.1,
  "estimatePetgDensity": 1.27,
  "estimateCopyDensity": 1.05,
  "estimateStockingWallMm": 5,
  "estimateStockingWallLargeMm": 6,
  "estimateConvexity": 1.1,
  "estimateFlangeCm3": 5,
  "estimateCutWallMm": 6,
  "estimateCutRibMm": 10,
  "estimateBrickOffsetMm": 4,
  "estimateBrickOffsetLargeMm": 10,
  "estimateCupWallMm": 6.5,
  "estimateReliefWallMm": 8,
  "estimateReliefBaseFactor": 1.2,
  "estimateMatrixWallMm": 4,
  "estimateClusterWallMm": 5,
  "estimateCompositeOffsetMm": 10,
  "estimateCompositeLocksShare": 0.10,
  "estimateShellHeightMm": 55,
  "estimateShellWallMm": 2,
  "estimateShellFactorStable": 1.3,
  "estimateShellFactorUnstable": 2.6,
  "estimateTinWallMm": 10,
  "estimateTinConvexity": 1.15,
  "estimateFdmGramsPerHour": 25,
  "estimateSlaHoursPerMm": 0.1,
  "estimateFdmProjectHours": 1,
  "estimateProcessingHours": 1
}$rates$, now(), NULL, 'Регламент ценообразования v3.5');

CREATE TABLE order_calculations (
    id               BIGSERIAL PRIMARY KEY,
    client           VARCHAR(255),
    title            VARCHAR(500),
    total_rub        DOUBLE PRECISION,
    margin           DOUBLE PRECISION,
    preliminary      BOOLEAN                     NOT NULL DEFAULT FALSE,
    has_estimates    BOOLEAN                     NOT NULL DEFAULT FALSE,
    has_exceptions   BOOLEAN                     NOT NULL DEFAULT FALSE,
    below_min_margin BOOLEAN                     NOT NULL DEFAULT FALSE,
    comment          TEXT,
    request          TEXT                        NOT NULL,
    result           TEXT                        NOT NULL,
    rates_id         BIGINT REFERENCES calculator_rates (id),
    created_by_email VARCHAR(255)                NOT NULL,
    created_by_name  VARCHAR(255),
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_order_calculations_created_at ON order_calculations (created_at DESC);
