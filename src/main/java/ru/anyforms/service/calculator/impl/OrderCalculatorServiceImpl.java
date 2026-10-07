package ru.anyforms.service.calculator.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.calculator.CalculationDiscountRequest;
import ru.anyforms.dto.calculator.CalculationHint;
import ru.anyforms.dto.calculator.CalculationOption;
import ru.anyforms.dto.calculator.CalculationPositionRequest;
import ru.anyforms.dto.calculator.CalculationVariantRequest;
import ru.anyforms.dto.calculator.CalculatorOptionsDTO;
import ru.anyforms.dto.calculator.CalculatorRates;
import ru.anyforms.dto.calculator.CostBreakdown;
import ru.anyforms.dto.calculator.KpPrice;
import ru.anyforms.dto.calculator.OrderCalculationRequest;
import ru.anyforms.dto.calculator.OrderCalculationResult;
import ru.anyforms.dto.calculator.OrderCalculationSummary;
import ru.anyforms.dto.calculator.ParameterSource;
import ru.anyforms.dto.calculator.PositionCalculation;
import ru.anyforms.dto.calculator.PriceBreakdown;
import ru.anyforms.dto.calculator.PriceInput;
import ru.anyforms.model.calculator.FormModifier;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.model.calculator.MasterType;
import ru.anyforms.model.calculator.PourMaterial;
import ru.anyforms.model.calculator.SiliconeType;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.calculator.CalculatorPermissions;
import ru.anyforms.service.calculator.CalculatorRatesService;
import ru.anyforms.service.calculator.OrderCalculatorService;
import ru.anyforms.service.calculator.ParameterEstimator;
import ru.anyforms.service.calculator.PriceCalculator;
import ru.anyforms.service.calculator.RatesSnapshot;
import ru.anyforms.service.calculator.ResolvedVariant;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ru.anyforms.service.calculator.impl.CalculatorMath.isTrue;
import static ru.anyforms.service.calculator.impl.CalculatorMath.orZero;
import static ru.anyforms.service.calculator.impl.CalculatorMath.roundToStep;
import static ru.anyforms.service.calculator.impl.CalculatorMath.rub;
import static ru.anyforms.service.calculator.impl.CalculatorMath.share;

@Service
@RequiredArgsConstructor
class OrderCalculatorServiceImpl implements OrderCalculatorService {

    static final String EXCEPTIONS_FORBIDDEN = "Исключения — ручную цену модели или формы, число комплектов, "
            + "бонусную позицию, скидку на разработку и маржу ниже минимума — задаёт только основатель";

    private static final double EPSILON = 1e-9;

    private final PriceCalculator priceCalculator;
    private final ParameterEstimator parameterEstimator;
    private final CalculatorRatesService calculatorRatesService;

    @Override
    public OrderCalculationResult calculate(OrderCalculationRequest request, UserAccess user) {
        if (request.hasExceptions() && !CalculatorPermissions.isFounder(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, EXCEPTIONS_FORBIDDEN);
        }
        return calculate(request, calculatorRatesService.current());
    }

    @Override
    public OrderCalculationResult calculate(OrderCalculationRequest request, RatesSnapshot snapshot) {
        requireModelPrices(request);
        CalculatorRates r = snapshot.rates();
        CalculationDiscountRequest discount = request.getDiscount() == null
                ? new CalculationDiscountRequest()
                : request.getDiscount();

        List<PositionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < request.getPositions().size(); i++) {
            drafts.add(draft(i, request.getPositions().get(i), r));
        }

        double requestedForms = clampPercent(discount.getFormsPercent());
        double developmentPercent = clampPercent(discount.getDevelopmentPercent());
        double promoPercent = clampPercent(discount.getPromoPercent());
        boolean allowBelow = isTrue(discount.getAllowBelowMinMargin());

        double appliedForms = requestedForms;
        Totals totals = totals(drafts, new Discounts(appliedForms, developmentPercent, promoPercent), r);
        if (!allowBelow && requestedForms > 0 && totals.belowMin(r)) {
            appliedForms = 0;
            for (double candidate = Math.ceil(requestedForms) - 1; candidate > 0; candidate--) {
                if (!totals(drafts, new Discounts(candidate, developmentPercent, promoPercent), r).belowMin(r)) {
                    appliedForms = candidate;
                    break;
                }
            }
            totals = totals(drafts, new Discounts(appliedForms, developmentPercent, promoPercent), r);
        }
        Discounts applied = new Discounts(appliedForms, developmentPercent, promoPercent);
        boolean capped = appliedForms < requestedForms - EPSILON;

        List<PositionCalculation> positions = drafts.stream().map(d -> d.finish(applied, r)).toList();
        OrderCalculationSummary summary = summary(totals, requestedForms, applied, capped, allowBelow, r);
        boolean preliminary = drafts.stream().anyMatch(PositionDraft::preliminary);
        int estimated = drafts.stream().mapToInt(PositionDraft::estimatedFields).sum();
        List<CalculationHint> hints = orderHints(request, discount, summary, preliminary, estimated, r);

        return new OrderCalculationResult(positions, summary, preliminary, estimated > 0, request.hasExceptions(),
                hints, snapshot.versionId());
    }

    @Override
    public CalculatorOptionsDTO options(UserAccess user) {
        return new CalculatorOptionsDTO(
                Arrays.stream(FormType.values())
                        .map(t -> new CalculatorOptionsDTO.FormTypeOption(t.name(), t.getLabel(), t.getHint(), t.isCut()))
                        .toList(),
                Arrays.stream(PourMaterial.values())
                        .map(m -> new CalculatorOptionsDTO.Option(m.name(), m.getLabel(),
                                m.isPlatinumOnly() ? "только платина" : m.isHeavy() ? "тяжёлая заливка" : null))
                        .toList(),
                Arrays.stream(SiliconeType.values())
                        .map(s -> new CalculatorOptionsDTO.Option(s.name(), s.getLabel(), s.getDescription()))
                        .toList(),
                Arrays.stream(MasterType.values())
                        .map(m -> new CalculatorOptionsDTO.Option(m.name(), m.getLabel(), null))
                        .toList(),
                Arrays.stream(FormModifier.values())
                        .map(m -> new CalculatorOptionsDTO.Option(m.name(), m.getLabel(), m.getHint()))
                        .toList(),
                CalculatorPermissions.isFounder(user));
    }

    private PositionDraft draft(int index, CalculationPositionRequest p, CalculatorRates r) {
        boolean digital = isTrue(p.getDigitalOnly());
        boolean bonus = isTrue(p.getBonus());
        List<SiliconeType> silicones = distinct(p.getSilicones());
        if (silicones.isEmpty()) {
            silicones = List.of(SiliconeType.PLATINUM);
        }
        List<Integer> tirages = distinct(p.getTirages());
        boolean noTirage = tirages.isEmpty();
        if (noTirage || digital) {
            tirages = List.of(noTirage ? 1 : tirages.get(0));
        }
        if (digital) {
            silicones = List.of(silicones.get(0));
        }

        List<OptionDraft> options = new ArrayList<>();
        LinkedHashMap<String, CalculationHint> hints = new LinkedHashMap<>();
        for (int v = 0; v < p.getVariants().size(); v++) {
            CalculationVariantRequest variant = p.getVariants().get(v);
            for (SiliconeType silicone : silicones) {
                for (int tirage : tirages) {
                    ResolvedVariant resolved = parameterEstimator.resolve(p, variant, silicone, tirage, r);
                    resolved.hints().forEach(h -> hints.putIfAbsent(h.code(), h));
                    PriceBreakdown price = priceCalculator.price(resolved.input(), r);
                    CostBreakdown cost = priceCalculator.cost(resolved.input(), price, r);
                    List<CalculationHint> optionHints = optionHints(resolved.input(), price, r);
                    if (silicone == SiliconeType.TIN && p.getPourMaterial() != null && p.getPourMaterial().isPlatinumOnly()) {
                        optionHints.add(0, CalculationHint.error("PLATINUM_ONLY_OPTION",
                                "Олово для изделия «" + p.getPourMaterial().getLabel() + "» не предлагаем — только платина"));
                    }
                    options.add(new OptionDraft(v, variant.getFormType(), silicone, tirage, resolved, price, cost,
                            kp(price, tirage, r), optionHints));
                }
            }
        }

        int selected = selectedIndex(options, p, silicones, tirages, digital);
        boolean noDims = !(orZero(p.getWidthMm()) > 0 && orZero(p.getDepthMm()) > 0 && orZero(p.getHeightMm()) > 0);
        boolean noModel = !isTrue(p.getHasClientModel()) && !isTrue(p.getSharedModel()) && p.getVariants().stream()
                .noneMatch(OrderCalculatorServiceImpl::hasMeasuredGeometry);
        boolean preliminary = digital ? noDims : noDims || noTirage || noModel;

        List<CalculationHint> positionHints = new ArrayList<>();
        if (bonus) {
            positionHints.add(CalculationHint.info("BONUS",
                    "Бонусная позиция: клиенту 0 ₽, затраты учитываются в рентабельности"));
        }
        if (digital) {
            positionHints.add(CalculationHint.info("DIGITAL_ONLY",
                    "Цифровой продукт: только моделирование и проектирование, без печати и форм"));
        }
        if (p.getPourMaterial() != null && p.getPourMaterial().isPlatinumOnly() && silicones.contains(SiliconeType.TIN)) {
            positionHints.add(CalculationHint.error("PLATINUM_ONLY",
                    "Для изделий «" + p.getPourMaterial().getLabel() + "» — только платина: варианты с оловом не предлагайте"));
        }
        if (noTirage && !digital) {
            positionHints.add(CalculationHint.warning("NO_TIRAGE", "Тираж не указан — посчитано для одной формы"));
        }
        if (preliminary) {
            List<String> missing = new ArrayList<>();
            if (noDims) {
                missing.add("размеров изделия");
            }
            if (noTirage && !digital) {
                missing.add("тиража");
            }
            if (noModel && !digital) {
                missing.add("3D-модели (геометрия мастер-модели оценена)");
            }
            positionHints.add(CalculationHint.warning("PRELIMINARY",
                    "Не хватает " + String.join(", ", missing) + " — это предварительная оценка"));
        }
        if (isTrue(p.getSharedModel())) {
            positionHints.add(CalculationHint.info("SHARED_MODEL", "Модель учтена в другой позиции — здесь 0"));
        }
        if (isTrue(p.getSharedSlaPrint())) {
            positionHints.add(CalculationHint.info("SHARED_PRINT",
                    "SLA-печать учтена в другой позиции (одна печать на стол) — здесь 0"));
        }
        positionHints.addAll(hints.values());

        int estimatedFields = (int) options.get(selected).resolved().sources().values().stream()
                .filter(s -> s == ParameterSource.ESTIMATE)
                .count();

        return new PositionDraft(index, p.getProductName(), digital, bonus, preliminary, options, selected,
                positionHints, estimatedFields);
    }

    private int selectedIndex(List<OptionDraft> options, CalculationPositionRequest p, List<SiliconeType> silicones,
                              List<Integer> tirages, boolean digital) {
        int variant = p.getSelectedVariant() == null ? 0 : p.getSelectedVariant();
        SiliconeType silicone = p.getSelectedSilicone() == null || digital ? silicones.get(0) : p.getSelectedSilicone();
        int tirage = p.getSelectedTirage() == null || digital ? tirages.get(0) : p.getSelectedTirage();
        for (int i = 0; i < options.size(); i++) {
            OptionDraft o = options.get(i);
            if (o.variantIndex() == variant && o.silicone() == silicone && o.tirage() == tirage) {
                return i;
            }
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).variantIndex() == variant) {
                return i;
            }
        }
        return 0;
    }

    private List<CalculationHint> optionHints(PriceInput in, PriceBreakdown p, CalculatorRates r) {
        List<CalculationHint> hints = new ArrayList<>();
        if (in.digitalOnly()) {
            return hints;
        }
        boolean kitsStep = r.getKitsByTirage().stream().anyMatch(s -> s.from() > 1 && s.from() == in.tirage());
        if (in.needsIntermediate() && in.kitsOverride() == null && kitsStep) {
            hints.add(CalculationHint.info("KITS_STEP", "На тираже " + in.tirage()
                    + " добавляется производственный комплект — итог вырастает скачком"));
        }
        if (p.kits() > p.kitsPaid()) {
            hints.add(CalculationHint.info("KITS_PAID_CAP", "Нужно " + p.kits() + " производственных комплектов, клиент оплачивает "
                    + p.kitsPaid()));
        }
        if (p.minPriceApplied()) {
            hints.add(CalculationHint.info("MIN_FORM_PRICE", "Цена формы определяется минимумом по тиражу ("
                    + money(p.minFormPrice()) + "), а не расчётом"));
        }
        if (p.cliffApplied()) {
            hints.add(CalculationHint.info("CLIFF", "Защита от «обрыва»: формы не дешевле " + money(p.cliffFloor())
                    + " — иначе больший тираж вышел бы дешевле меньшего"));
        }
        if (p.formPriceOverridden()) {
            hints.add(CalculationHint.info("FORM_PRICE_OVERRIDE", "Цена формы задана вручную — исключение основателя"));
        }
        if (in.siliconeGrams() >= r.getWeightReserveThresholdG()) {
            hints.add(CalculationHint.info("HEAVY_FORM", "Форма тяжелее " + number(r.getWeightReserveThresholdG())
                    + " г — вес силикона без запаса"));
        }
        return hints;
    }

    private KpPrice kp(PriceBreakdown p, int tirage, CalculatorRates r) {
        double step = r.getKpRoundingStep();
        double development = roundToStep(p.development(), step);
        double formPrice = tirage > 0 ? roundToStep(p.formsTotal() / tirage, step) : 0;
        return kpPrice(development, formPrice, tirage);
    }

    private static KpPrice offer(KpPrice kp, boolean bonus, Discounts d, CalculatorRates r) {
        if (bonus) {
            return KpPrice.zero(kp.tirage());
        }
        double step = r.getKpRoundingStep();
        double promo = 1 - share(d.promo());
        double development = roundToStep(kp.development() * (1 - share(d.development())) * promo, step);
        double formPrice = roundToStep(kp.formPrice() * (1 - share(d.forms())) * promo, step);
        return kpPrice(development, formPrice, kp.tirage());
    }

    private static KpPrice kpPrice(double development, double formPrice, int tirage) {
        double forms = rub(formPrice * tirage);
        double total = rub(development + forms);
        return new KpPrice(development, formPrice, tirage, forms, total, tirage > 0 ? Math.round(total / tirage) : total);
    }

    private Totals totals(List<PositionDraft> drafts, Discounts d, CalculatorRates r) {
        double developmentKp = 0;
        double formsKp = 0;
        double developmentOffer = 0;
        double formsOffer = 0;
        double cost = 0;
        for (PositionDraft draft : drafts) {
            OptionDraft option = draft.selected();
            cost += option.cost().total();
            if (draft.bonus()) {
                continue;
            }
            KpPrice offer = offer(option.kp(), false, d, r);
            developmentKp += option.kp().development();
            formsKp += option.kp().forms();
            developmentOffer += offer.development();
            formsOffer += offer.forms();
        }
        return new Totals(rub(developmentKp), rub(formsKp), rub(developmentOffer), rub(formsOffer), rub(cost));
    }

    private OrderCalculationSummary summary(Totals t, double requestedForms, Discounts applied, boolean capped,
                                            boolean allowBelow, CalculatorRates r) {
        double totalKp = t.totalKp();
        double totalOffer = t.totalOffer();
        double profit = totalOffer * (1 - r.getTaxRate() - r.getManagerCommission()) - t.cost();
        Double margin = margin(totalOffer, t.cost(), r);
        double divisor = 1 - r.getTaxRate() - r.getManagerCommission() - r.getMinProfitShare();
        double minPrice = divisor > 0 ? t.cost() / divisor : t.cost();
        double maxDiscountRub = Math.max(0, totalKp - minPrice);
        return OrderCalculationSummary.builder()
                .developmentKp(t.developmentKp())
                .formsKp(t.formsKp())
                .totalKp(totalKp)
                .totalOffer(totalOffer)
                .discountRub(rub(totalKp - totalOffer))
                .formsDiscountRequested(requestedForms)
                .formsDiscountApplied(applied.forms())
                .formsDiscountCapped(capped)
                .developmentDiscount(applied.development())
                .promoDiscount(applied.promo())
                .belowMinMarginAllowed(allowBelow)
                .cost(t.cost())
                .profit(rub(profit))
                .margin(margin == null ? null : Math.round(margin * 10_000) / 10_000.0)
                .minPrice(rub(minPrice))
                .maxDiscountRub(rub(maxDiscountRub))
                .maxDiscountShare(totalKp > 0 ? Math.round(maxDiscountRub / totalKp * 10_000) / 10_000.0 : 0)
                .maxFormsDiscountShare(t.formsKp() > 0
                        ? Math.round(Math.min(1, maxDiscountRub / t.formsKp()) * 10_000) / 10_000.0
                        : 0)
                .minProfitShare(r.getMinProfitShare())
                .targetProfitShare(r.getTargetProfitShareTypical())
                .belowMinMargin(margin != null && margin < r.getMinProfitShare() - EPSILON)
                .offerValidityDays(r.getOfferValidityDays())
                .build();
    }

    private List<CalculationHint> orderHints(OrderCalculationRequest request, CalculationDiscountRequest discount,
                                             OrderCalculationSummary s, boolean preliminary, int estimated,
                                             CalculatorRates r) {
        List<CalculationHint> hints = new ArrayList<>();
        if (preliminary) {
            hints.add(CalculationHint.warning("PRELIMINARY",
                    "Мало данных (нет модели, размеров или тиража) — оформляйте как «Предварительная оценка»"));
        }
        if (estimated > 0) {
            hints.add(CalculationHint.warning("ESTIMATES", "Оценкой посчитано полей: " + estimated
                    + " — подтвердите их до отправки КП"));
        }
        if (s.formsDiscountCapped()) {
            hints.add(CalculationHint.warning("FORMS_DISCOUNT_CAPPED", "Скидка на формы " + formatPercent(s.formsDiscountRequested())
                    + " опускает маржу ниже " + formatShare(s.minProfitShare()) + " — применена максимальная скидка "
                    + formatPercent(s.formsDiscountApplied()) + ". В КП — «максимальная скидка»"));
        }
        if (s.belowMinMargin()) {
            String text = "Маржа " + formatShare(s.margin()) + " ниже минимума " + formatShare(s.minProfitShare());
            hints.add(s.belowMinMarginAllowed()
                    ? CalculationHint.warning("BELOW_MIN_MARGIN_APPROVED", text + " — по решению основателя")
                    : CalculationHint.error("BELOW_MIN_MARGIN", text + ". Нужно решение основателя"));
        } else if (s.margin() != null && s.margin() < s.targetProfitShare() - EPSILON) {
            hints.add(CalculationHint.info("BELOW_TARGET_MARGIN", "Маржа " + formatShare(s.margin())
                    + " — ниже ориентира типового проекта " + formatShare(s.targetProfitShare())));
        }
        for (int i = 0; i < request.getPositions().size(); i++) {
            CalculationPositionRequest p = request.getPositions().get(i);
            if (p.hasExceptions() && isBlank(p.getExceptionComment())) {
                hints.add(CalculationHint.warning("EXCEPTION_COMMENT", "Позиция " + (i + 1)
                        + ": у исключения нет комментария «исключение проекта» — без него расчёт не сохранить"));
            }
        }
        boolean discountNeedsComment = discount.getDevelopmentPercent() != null && discount.getDevelopmentPercent() > 0
                || (isTrue(discount.getAllowBelowMinMargin()) && s.belowMinMargin());
        if (discountNeedsComment && isBlank(discount.getComment())) {
            hints.add(CalculationHint.warning("DISCOUNT_COMMENT",
                    "Скидка по решению основателя — укажите комментарий, без него расчёт не сохранить"));
        }
        if (r.getMinProfitShare() + r.getTaxRate() + r.getManagerCommission() >= 1) {
            hints.add(CalculationHint.error("RATES_INVALID", "Налог, комиссия и минимальная маржа в ставках дают 100% и больше"));
        }
        return hints;
    }

    private static void requireModelPrices(OrderCalculationRequest request) {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < request.getPositions().size(); i++) {
            CalculationPositionRequest position = request.getPositions().get(i);
            if (isTrue(position.getHasClientModel()) || isTrue(position.getSharedModel())) {
                continue;
            }
            List<CalculationVariantRequest> variants = position.getVariants();
            for (int v = 0; v < variants.size(); v++) {
                if (variants.get(v).getModelContractorPrice() == null) {
                    missing.add("позиция " + (i + 1)
                            + (isBlank(position.getProductName()) ? "" : " «" + position.getProductName().trim() + "»")
                            + (variants.size() > 1 ? ", вариант " + (v + 1) : ""));
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Укажите цену художника за модель: " + String.join("; ", missing));
        }
    }

    private static boolean hasMeasuredGeometry(CalculationVariantRequest variant) {
        return orZero(variant.getSlaAreaCm2()) > 0 || orZero(variant.getSlaMlManual()) > 0;
    }

    static Double margin(double revenue, double cost, CalculatorRates r) {
        if (revenue <= 0) {
            return null;
        }
        return (revenue * (1 - r.getTaxRate() - r.getManagerCommission()) - cost) / revenue;
    }

    private static double clampPercent(Double value) {
        if (value == null || value.isNaN()) {
            return 0;
        }
        return Math.min(Math.max(value, 0), 100);
    }

    private static <T> List<T> distinct(List<T> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values.stream().filter(Objects::nonNull).toList()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String money(double value) {
        return number(Math.round(value)) + " ₽";
    }

    static String number(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(value);
    }

    private static String formatPercent(double value) {
        return number(Math.round(value * 10) / 10.0) + "%";
    }

    private static String formatShare(Double value) {
        return value == null ? "—" : formatPercent(value * 100);
    }

    private record Discounts(double forms, double development, double promo) {
    }

    private record Totals(double developmentKp, double formsKp, double developmentOffer, double formsOffer, double cost) {

        double totalKp() {
            return rub(developmentKp + formsKp);
        }

        double totalOffer() {
            return rub(developmentOffer + formsOffer);
        }

        boolean belowMin(CalculatorRates r) {
            Double margin = margin(totalOffer(), cost, r);
            return margin != null && margin < r.getMinProfitShare() - EPSILON;
        }
    }

    private record OptionDraft(int variantIndex, FormType formType, SiliconeType silicone, int tirage,
                               ResolvedVariant resolved, PriceBreakdown price, CostBreakdown cost, KpPrice kp,
                               List<CalculationHint> hints) {

        CalculationOption finish(boolean bonus, Discounts discounts, CalculatorRates r) {
            KpPrice offer = offer(kp, bonus, discounts, r);
            return new CalculationOption(variantIndex, formType, silicone, tirage, resolved.input(),
                    resolved.sources(), price, kp, offer, cost,
                    bonus ? null : roundShare(margin(offer.total(), cost.total(), r)), hints, developmentWorks());
        }

        private List<String> developmentWorks() {
            List<String> works = new ArrayList<>();
            if (price.model() > 0) {
                works.add("model");
            }
            if (price.sla() > 0) {
                works.add("sla");
            }
            if (price.fdmProject() > 0) {
                works.add("fdmProject");
            }
            if (price.kit() > 0) {
                works.add("kit");
            }
            if (price.processing() > 0) {
                works.add("processing");
            }
            if (price.cnc() > 0) {
                works.add("cnc");
            }
            if (price.prep() > 0) {
                works.add("prep");
            }
            if (price.tin() > 0 || price.copy() > 0) {
                works.add("intermediate");
            }
            if (price.extraKits() > 0) {
                works.add("extraKits");
            }
            return works;
        }

        private static Double roundShare(Double value) {
            return value == null ? null : Math.round(value * 10_000) / 10_000.0;
        }
    }

    private record PositionDraft(int index, String productName, boolean digital, boolean bonus, boolean preliminary,
                                 List<OptionDraft> options, int selectedIndex, List<CalculationHint> hints,
                                 int estimatedFields) {

        OptionDraft selected() {
            return options.get(selectedIndex);
        }

        PositionCalculation finish(Discounts discounts, CalculatorRates r) {
            return new PositionCalculation(index, productName, digital, bonus, preliminary,
                    options.stream().map(o -> o.finish(bonus, discounts, r)).toList(), selectedIndex, hints);
        }
    }
}
