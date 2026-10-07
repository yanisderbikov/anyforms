package ru.anyforms.service.calculator.impl;

import org.springframework.stereotype.Component;
import ru.anyforms.dto.calculator.CalculationHint;
import ru.anyforms.dto.calculator.CalculationPositionRequest;
import ru.anyforms.dto.calculator.CalculationVariantRequest;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.ParameterSource;
import ru.anyforms.dto.calculator.PriceInput;
import ru.anyforms.model.calculator.FormModifier;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.model.calculator.MasterType;
import ru.anyforms.model.calculator.SiliconeType;
import ru.anyforms.service.calculator.ParameterEstimator;
import ru.anyforms.service.calculator.ResolvedVariant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static ru.anyforms.service.calculator.impl.CalculatorMath.isTrue;
import static ru.anyforms.service.calculator.impl.CalculatorMath.orZero;
import static ru.anyforms.service.calculator.impl.CalculatorMath.rub;

@Component
class FormulaParameterEstimator implements ParameterEstimator {

    @Override
    public ResolvedVariant resolve(CalculationPositionRequest position,
                                   CalculationVariantRequest variant,
                                   SiliconeType silicone,
                                   int tirage,
                                   CalculatorRates r) {
        Sources sources = new Sources();
        List<CalculationHint> hints = new ArrayList<>();
        FormType type = variant.getFormType();
        MasterType master = variant.getMasterType() == null ? MasterType.SLA : variant.getMasterType();
        boolean digital = isTrue(position.getDigitalOnly());
        boolean clientModel = isTrue(position.getHasClientModel());
        boolean sharedModel = isTrue(position.getSharedModel());
        boolean sharedPrint = isTrue(position.getSharedSlaPrint());
        Geometry g = Geometry.of(position, variant, r);

        double contractor = orZero(variant.getModelContractorPrice());
        double fdmProjectHours = sources.resolve("fdmProjectHours", variant.getFdmProjectHours(),
                r.getEstimateFdmProjectHours(), ParameterSource.ESTIMATE);

        PriceInput.PriceInputBuilder input = PriceInput.builder()
                .silicone(silicone)
                .tirage(tirage)
                .hasClientModel(clientModel)
                .digitalOnly(digital)
                .sharedModel(sharedModel)
                .sharedSlaPrint(sharedPrint)
                .modelContractorPrice(contractor)
                .modelPriceOverride(variant.getModelPriceOverride())
                .fdmProjectHours(fdmProjectHours)
                .textureFactor(variant.getTextureFactor() == null ? 1 : variant.getTextureFactor())
                .kitsOverride(variant.getKitsOverride())
                .formPriceOverride(variant.getFormPriceOverride())
                .tinFormsCount(1)
                .weightReserve(r.getWeightReserveDefault());

        if (digital) {
            return new ResolvedVariant(input.build(), sources.map(), hints);
        }

        boolean slaMaster = master == MasterType.SLA && !sharedPrint;
        boolean manualMl = variant.getSlaMlManual() != null && variant.getSlaMlManual() > 0;
        boolean estimateSla = slaMaster && !manualMl;
        input.slaMlManual(orZero(variant.getSlaMlManual()))
                .slaAreaCm2(estimateSla
                        ? sources.resolve("slaAreaCm2", variant.getSlaAreaCm2(), orZero(g.area()), ParameterSource.ESTIMATE)
                        : orZero(variant.getSlaAreaCm2()))
                .slaVolumeCm3(estimateSla
                        ? sources.resolve("slaVolumeCm3", variant.getSlaVolumeCm3(), orZero(g.volume()), ParameterSource.ESTIMATE)
                        : orZero(variant.getSlaVolumeCm3()))
                .slaHours(slaMaster
                        ? sources.resolve("slaHours", variant.getSlaHours(),
                        g.hasHeight() ? rub(g.heightMm() * r.getEstimateSlaHoursPerMm()) : 0, ParameterSource.ESTIMATE)
                        : orZero(variant.getSlaHours()));

        Double siliconeEstimate = estimateSiliconeGrams(type, g, variant, r);
        double siliconeGrams = sources.resolve("siliconeGrams", variant.getSiliconeGrams(),
                siliconeEstimate == null ? 0 : siliconeEstimate, ParameterSource.ESTIMATE);
        if (variant.getSiliconeGrams() == null && siliconeEstimate == null) {
            hints.add(CalculationHint.warning("NO_GEOMETRY",
                    "Нет габаритов и площади/объёма мастер-модели — вес силикона не оценить: укажите размеры или вес"));
        }

        boolean shellRequired = variant.getShellRequired() != null
                ? variant.getShellRequired()
                : shellByRule(type, position, siliconeGrams, r);
        Double envelopeArea = formEnvelopeArea(type, g, variant, r);
        double shellGrams = shellRequired
                ? sources.resolve("shellGrams", variant.getShellGrams(),
                envelopeArea == null ? 0 : rub(petgShell(envelopeArea, isTrue(variant.getShellUnstable()), r)),
                ParameterSource.ESTIMATE)
                : sources.resolve("shellGrams", variant.getShellGrams(), 0, ParameterSource.DEFAULT);

        double kitEstimate = envelopeArea == null ? 0
                : rub(petgShell(envelopeArea, false, r) * (type == FormType.COMPOSITE ? 2 : 1));
        double kitGrams = sources.resolve("kitGrams", variant.getKitGrams(), kitEstimate, ParameterSource.ESTIMATE);
        double kitHours = sources.resolve("kitHours", variant.getKitHours(),
                rub(kitGrams / r.getEstimateFdmGramsPerHour()), ParameterSource.ESTIMATE);

        boolean needsIntermediate = variant.getNeedsIntermediate() != null
                ? variant.getNeedsIntermediate()
                : silicone == SiliconeType.PLATINUM && master == MasterType.SLA;
        sources.flag("needsIntermediate", variant.getNeedsIntermediate() != null);

        int tinForms = variant.getTinFormsCount() != null
                ? variant.getTinFormsCount()
                : type.defaultTinForms(variant.getSetFormsCount());
        double tinGrams = 0;
        double copyGrams = 0;
        if (needsIntermediate) {
            sources.flag("tinFormsCount", variant.getTinFormsCount() != null);
            Double tinEstimate = g.area() == null ? null
                    : (g.area() * mm(r.getEstimateTinWallMm()) * r.getEstimateTinConvexity() + r.getEstimateFlangeCm3())
                    * r.getEstimateSiliconeDensity();
            tinGrams = sources.resolve("tinGrams", variant.getTinGrams(),
                    tinEstimate == null ? 0 : rub(tinEstimate), ParameterSource.ESTIMATE);
            copyGrams = sources.resolve("copyGrams", variant.getCopyGrams(),
                    g.volume() == null ? 0 : rub(g.volume() * r.getEstimateCopyDensity()), ParameterSource.ESTIMATE);
            if (type == FormType.SET || type == FormType.COMPOSITE) {
                hints.add(CalculationHint.info("EXTRA_COPY_TO_PREP",
                        "Вторая и последующие копии (у составных и комплектов) — добавьте в «Подготовку»"));
            }
        } else {
            tinGrams = orZero(variant.getTinGrams());
            copyGrams = orZero(variant.getCopyGrams());
        }

        int pours = type.pours(variant.getSetFormsCount()) + modifiersCount(variant);
        double extraPerForm = sources.resolve("extraPerFormRub", variant.getExtraPerFormRub(),
                r.getPourRubPerCast() * Math.max(pours - 1, 0), ParameterSource.DEFAULT);
        boolean hasCut = variant.getHasCut() != null ? variant.getHasCut() : type.isCut();
        sources.flag("hasCut", variant.getHasCut() != null);

        input.kitGrams(kitGrams)
                .kitHours(kitHours)
                .processingHours(sources.resolve("processingHours", variant.getProcessingHours(),
                        r.getEstimateProcessingHours(), ParameterSource.ESTIMATE))
                .cncHours(sources.resolve("cncHours", variant.getCncHours(), 0, ParameterSource.DEFAULT))
                .prepRub(sources.resolve("prepRub", variant.getPrepRub(), r.getPrepDefaultRub(), ParameterSource.DEFAULT))
                .siliconeGrams(siliconeGrams)
                .shellGrams(shellGrams)
                .needsIntermediate(needsIntermediate)
                .tinGrams(tinGrams)
                .tinFormsCount(tinForms)
                .copyGrams(copyGrams)
                .hasCut(hasCut)
                .extraPerFormRub(extraPerForm)
                .weightReserve(sources.resolve("weightReserve", variant.getWeightReserve(),
                        r.getWeightReserveDefault(), ParameterSource.DEFAULT));

        if (variant.getModifiers() != null) {
            variant.getModifiers().stream().filter(Objects::nonNull).distinct()
                    .forEach(m -> hints.add(CalculationHint.info("MODIFIER_" + m.name(), m.getLabel() + ": " + m.getHint())));
        }
        typeHint(type).ifPresent(hints::add);

        return new ResolvedVariant(input.build(), sources.map(), hints);
    }

    private Double estimateSiliconeGrams(FormType type, Geometry g, CalculationVariantRequest variant, CalculatorRates r) {
        Double volume = estimateSiliconeVolume(type, g, variant, r);
        return volume == null ? null : rub(Math.max(volume, 0) * r.getEstimateSiliconeDensity());
    }

    private Double estimateSiliconeVolume(FormType type, Geometry g, CalculationVariantRequest variant, CalculatorRates r) {
        return switch (type) {
            case STOCKING, SET -> g.area() == null ? null
                    : g.area() * stockingWall(g, r) * r.getEstimateConvexity() + r.getEstimateFlangeCm3();
            case STOCKING_CUT -> {
                if (g.area() == null) {
                    yield null;
                }
                double rib = g.hasDims() ? g.h() * mm(r.getEstimateCutRibMm()) * mm(r.getEstimateCutRibMm()) : 0;
                yield g.area() * mm(r.getEstimateCutWallMm()) * r.getEstimateConvexity() + r.getEstimateFlangeCm3() + rib;
            }
            case BRICK -> {
                if (!g.hasDims() || g.volume() == null) {
                    yield null;
                }
                double o = brickOffset(g, r);
                yield (g.w() + 2 * o) * (g.d() + 2 * o) * (g.h() + o) - g.volume();
            }
            case CYLINDER -> {
                if (!g.hasDims() || g.volume() == null) {
                    yield null;
                }
                double o = brickOffset(g, r);
                double radius = Math.max(g.w(), g.d()) / 2 + o;
                yield Math.PI * radius * radius * (g.h() + o) - g.volume();
            }
            case CUP -> {
                if (!g.hasDims()) {
                    yield null;
                }
                double t = mm(r.getEstimateCupWallMm());
                double radius = Math.max(g.w(), g.d()) / 2;
                double area = g.areaFromModel() ? g.area() : 2 * (2 * Math.PI * radius * g.h() + Math.PI * radius * radius);
                double lip = 2 * Math.PI * radius * 2 * t * t;
                yield area * t + lip;
            }
            case RELIEF -> g.area() == null ? null
                    : g.area() * mm(r.getEstimateReliefWallMm()) * r.getEstimateReliefBaseFactor();
            case FLAT_MATRIX_BAR -> {
                if (!g.hasDims() || g.volume() == null) {
                    yield null;
                }
                int cells = cells(variant);
                double wall = mm(r.getEstimateMatrixWallMm());
                int cols = (int) Math.ceil(Math.sqrt(cells));
                int rows = (int) Math.ceil((double) cells / cols);
                double block = (cols * g.w() + (cols + 1) * wall) * (rows * g.d() + (rows + 1) * wall) * (g.h() + wall);
                yield block - cells * g.volume();
            }
            case FLAT_MATRIX_CLUSTER -> g.area() == null ? null
                    : cells(variant) * g.area() * mm(r.getEstimateClusterWallMm()) * r.getEstimateConvexity()
                    + r.getEstimateFlangeCm3();
            case COMPOSITE -> {
                if (!g.hasDims() || g.volume() == null) {
                    yield null;
                }
                double o = mm(r.getEstimateCompositeOffsetMm());
                double block = (g.w() + 2 * o) * (g.d() + 2 * o) * (g.h() + 2 * o);
                yield (block - g.volume()) * (1 + r.getEstimateCompositeLocksShare());
            }
        };
    }

    private Double formEnvelopeArea(FormType type, Geometry g, CalculationVariantRequest variant, CalculatorRates r) {
        if (!g.hasDims()) {
            return null;
        }
        double fw;
        double fd;
        double fh;
        if (type == FormType.FLAT_MATRIX_BAR || type == FormType.FLAT_MATRIX_CLUSTER) {
            double wall = mm(type == FormType.FLAT_MATRIX_BAR ? r.getEstimateMatrixWallMm() : r.getEstimateClusterWallMm());
            int cells = cells(variant);
            int cols = (int) Math.ceil(Math.sqrt(cells));
            int rows = (int) Math.ceil((double) cells / cols);
            fw = cols * g.w() + (cols + 1) * wall;
            fd = rows * g.d() + (rows + 1) * wall;
            fh = g.h() + wall;
        } else {
            double t = envelopeWall(type, g, r);
            fw = g.w() + 2 * t;
            fd = g.d() + 2 * t;
            fh = g.h() + (type == FormType.COMPOSITE ? 2 * t : t);
        }
        return 2 * (fw * fd + fw * fh + fd * fh);
    }

    private double envelopeWall(FormType type, Geometry g, CalculatorRates r) {
        return switch (type) {
            case STOCKING, SET -> stockingWall(g, r);
            case STOCKING_CUT -> mm(r.getEstimateCutWallMm());
            case BRICK, CYLINDER -> brickOffset(g, r);
            case CUP -> mm(r.getEstimateCupWallMm());
            case RELIEF -> mm(r.getEstimateReliefWallMm());
            case FLAT_MATRIX_BAR -> mm(r.getEstimateMatrixWallMm());
            case FLAT_MATRIX_CLUSTER -> mm(r.getEstimateClusterWallMm());
            case COMPOSITE -> mm(r.getEstimateCompositeOffsetMm());
        };
    }

    private double petgShell(double envelopeArea, boolean unstable, CalculatorRates r) {
        double factor = unstable ? r.getEstimateShellFactorUnstable() : r.getEstimateShellFactorStable();
        return envelopeArea * mm(r.getEstimateShellWallMm()) * factor * r.getEstimatePetgDensity();
    }

    private boolean shellByRule(FormType type, CalculationPositionRequest position, double siliconeGrams, CalculatorRates r) {
        return switch (type.getShellRule()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case TALL_OR_HEAVY_POUR -> (position.getHeightMm() != null && position.getHeightMm() > r.getEstimateShellHeightMm())
                    || (position.getPourMaterial() != null && position.getPourMaterial().isHeavy());
            case HEAVY_FORM -> siliconeGrams >= r.getWeightReserveThresholdG();
        };
    }

    private Optional<CalculationHint> typeHint(FormType type) {
        return switch (type) {
            case BRICK, CYLINDER -> Optional.of(CalculationHint.info("TYPE_PLYWOOD",
                    "Фанера и ЧПУ к каждой форме — в «Доплаты к форме», фрезеровка — в часы ЧПУ"));
            case FLAT_MATRIX_BAR -> Optional.of(CalculationHint.info("TYPE_MATRIX",
                    "Сборка матрицы — часы × ставка обработки в «Подготовку», дубли ячеек — в вес копии"));
            case FLAT_MATRIX_CLUSTER -> Optional.of(CalculationHint.info("TYPE_CLUSTER",
                    "Цена кластера «950 + 350 ₽ за каждую доп. ячейку» — только исключением основателя "
                            + "(цена формы вручную), на больших тиражах не применять"));
            default -> Optional.empty();
        };
    }

    private static int modifiersCount(CalculationVariantRequest variant) {
        if (variant.getModifiers() == null) {
            return 0;
        }
        Set<FormModifier> distinct = new HashSet<>(variant.getModifiers());
        distinct.remove(null);
        return distinct.size();
    }

    private static int cells(CalculationVariantRequest variant) {
        return variant.getCellsCount() == null ? 1 : Math.max(variant.getCellsCount(), 1);
    }

    private static double stockingWall(Geometry g, CalculatorRates r) {
        return mm(g.large() ? r.getEstimateStockingWallLargeMm() : r.getEstimateStockingWallMm());
    }

    private static double brickOffset(Geometry g, CalculatorRates r) {
        return mm(g.large() ? r.getEstimateBrickOffsetLargeMm() : r.getEstimateBrickOffsetMm());
    }

    private static double mm(double millimeters) {
        return millimeters / 10;
    }

    private record Geometry(double w, double d, double h, boolean hasDims, boolean hasHeight, double heightMm,
                            boolean large, Double area, boolean areaFromModel, Double volume) {

        static Geometry of(CalculationPositionRequest position, CalculationVariantRequest variant, CalculatorRates r) {
            double widthMm = orZero(position.getWidthMm());
            double depthMm = orZero(position.getDepthMm());
            double heightMm = orZero(position.getHeightMm());
            boolean hasDims = widthMm > 0 && depthMm > 0 && heightMm > 0;
            double w = widthMm / 10;
            double d = depthMm / 10;
            double h = heightMm / 10;
            boolean large = Math.max(widthMm, Math.max(depthMm, heightMm)) > r.getEstimateLargeSizeMm();

            boolean areaFromModel = variant.getSlaAreaCm2() != null && variant.getSlaAreaCm2() > 0;
            Double area = areaFromModel ? variant.getSlaAreaCm2()
                    : hasDims ? 2 * (w * d + w * h + d * h) * r.getEstimateAreaShare() : null;
            boolean volumeFromModel = variant.getSlaVolumeCm3() != null && variant.getSlaVolumeCm3() > 0;
            Double volume = volumeFromModel ? variant.getSlaVolumeCm3()
                    : hasDims ? w * d * h * r.getEstimateVolumeShare() : null;

            return new Geometry(w, d, h, hasDims, heightMm > 0, heightMm, large,
                    area == null ? null : rub(area), areaFromModel, volume == null ? null : rub(volume));
        }
    }

    private static final class Sources {
        private final Map<String, ParameterSource> map = new LinkedHashMap<>();

        double resolve(String field, Double value, double fallback, ParameterSource fallbackSource) {
            if (value != null) {
                return value;
            }
            map.put(field, fallbackSource);
            return fallback;
        }

        void flag(String field, boolean provided) {
            if (!provided) {
                map.put(field, ParameterSource.DEFAULT);
            }
        }

        Map<String, ParameterSource> map() {
            return map;
        }
    }
}
