package ru.anyforms.service.calculator.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.calculator.AiSuggestionRequest;
import ru.anyforms.dto.calculator.CalculationPositionRequest;
import ru.anyforms.dto.calculator.CalculationReferenceDTO;
import ru.anyforms.dto.calculator.CalculationVariantRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiReference;
import ru.anyforms.dto.calculator.ai.CalculationAiRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiSuggestion;
import ru.anyforms.exception.CalculationAiException;
import ru.anyforms.integration.CalculationAiGateway;
import ru.anyforms.model.calculator.SiliconeType;
import ru.anyforms.service.calculator.CalculatorAiService;
import ru.anyforms.service.calculator.CalculatorReferenceService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class CalculatorAiServiceImpl implements CalculatorAiService {

    private static final double MAX_DIMENSION_MM = 5000;
    private static final double MAX_GEOMETRY = 1_000_000;
    private static final double MAX_GRAMS = 1_000_000;
    private static final double MAX_HOURS = 500;
    private static final double MAX_RUB = 10_000_000;

    private final Optional<CalculationAiGateway> calculationAiGateway;
    private final CalculatorReferenceService calculatorReferenceService;

    @Override
    public boolean available() {
        return calculationAiGateway.isPresent();
    }

    @Override
    public String providerName() {
        return calculationAiGateway.map(CalculationAiGateway::providerName).orElse(null);
    }

    @Override
    public CalculationAiSuggestion suggest(AiSuggestionRequest request) {
        CalculationAiGateway gateway = calculationAiGateway.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "AI-провайдер для калькулятора не подключён"));
        CalculationPositionRequest position = request.getPosition();
        if (request.getVariantIndex() >= position.getVariants().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет такого варианта формы");
        }
        CalculationVariantRequest variant = position.getVariants().get(request.getVariantIndex());

        CalculationAiSuggestion suggestion;
        try {
            suggestion = gateway.suggestParameters(CalculationAiRequest.builder()
                    .productName(position.getProductName())
                    .description(request.getDescription())
                    .widthMm(position.getWidthMm())
                    .depthMm(position.getDepthMm())
                    .heightMm(position.getHeightMm())
                    .pourMaterial(position.getPourMaterial())
                    .formType(variant.getFormType())
                    .masterType(variant.getMasterType())
                    .silicone(first(position.getSilicones(), SiliconeType.PLATINUM))
                    .tirage(first(position.getTirages(), null))
                    .references(references(position.getReferences()))
                    .build());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (CalculationAiException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (Exception e) {
            log.error("AI-провайдер {} не ответил", gateway.providerName(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI-провайдер не ответил: " + e.getMessage());
        }
        if (suggestion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI-провайдер вернул пустой ответ");
        }
        return sanitize(suggestion, gateway.providerName());
    }

    private List<CalculationAiReference> references(List<CalculationReferenceDTO> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        Map<String, String> urls = calculatorReferenceService.viewUrls(
                references.stream().filter(Objects::nonNull).map(CalculationReferenceDTO::key).toList());
        return references.stream()
                .filter(ref -> ref != null && urls.containsKey(ref.key()))
                .map(ref -> new CalculationAiReference(ref.filename(), urls.get(ref.key())))
                .toList();
    }

    static CalculationAiSuggestion sanitize(CalculationAiSuggestion suggestion, String provider) {
        CalculationVariantRequest v = suggestion.variant();
        CalculationVariantRequest variant = v == null ? null : v.toBuilder()
                .setFormsCount(between(v.getSetFormsCount(), 2, 20))
                .cellsCount(between(v.getCellsCount(), 1, 500))
                .modifiers(v.getModifiers() == null ? null
                        : v.getModifiers().stream().filter(Objects::nonNull).distinct().limit(2).toList())
                .modelContractorPrice(amount(v.getModelContractorPrice(), MAX_RUB, 0))
                .slaAreaCm2(positive(v.getSlaAreaCm2(), MAX_GEOMETRY, 1))
                .slaVolumeCm3(positive(v.getSlaVolumeCm3(), MAX_GEOMETRY, 1))
                .slaMlManual(amount(v.getSlaMlManual(), MAX_GEOMETRY, 1))
                .slaHours(amount(v.getSlaHours(), MAX_HOURS, 2))
                .textureFactor(v.getTextureFactor() == null || !(v.getTextureFactor() > 0) ? null
                        : round(Math.min(Math.max(v.getTextureFactor(), 1), 3), 2))
                .fdmProjectHours(amount(v.getFdmProjectHours(), MAX_HOURS, 2))
                .kitGrams(amount(v.getKitGrams(), MAX_GRAMS, 1))
                .kitHours(amount(v.getKitHours(), MAX_HOURS, 2))
                .processingHours(amount(v.getProcessingHours(), MAX_HOURS, 2))
                .cncHours(amount(v.getCncHours(), MAX_HOURS, 2))
                .prepRub(amount(v.getPrepRub(), MAX_RUB, 0))
                .siliconeGrams(amount(v.getSiliconeGrams(), MAX_GRAMS, 1))
                .shellGrams(amount(v.getShellGrams(), MAX_GRAMS, 1))
                .tinGrams(amount(v.getTinGrams(), MAX_GRAMS, 1))
                .tinFormsCount(between(v.getTinFormsCount(), 1, 20))
                .copyGrams(amount(v.getCopyGrams(), MAX_GRAMS, 1))
                .extraPerFormRub(amount(v.getExtraPerFormRub(), MAX_RUB, 0))
                .weightReserve(amount(v.getWeightReserve(), 1, 3))
                .modelPriceOverride(null)
                .kitsOverride(null)
                .formPriceOverride(null)
                .aiFields(null)
                .build();
        return CalculationAiSuggestion.builder()
                .widthMm(positive(suggestion.widthMm(), MAX_DIMENSION_MM, 1))
                .depthMm(positive(suggestion.depthMm(), MAX_DIMENSION_MM, 1))
                .heightMm(positive(suggestion.heightMm(), MAX_DIMENSION_MM, 1))
                .variant(variant)
                .notes(suggestion.notes() == null ? Map.of() : suggestion.notes())
                .summary(suggestion.summary())
                .provider(suggestion.provider() == null ? provider : suggestion.provider())
                .build();
    }

    private static Double positive(Double value, double max, int digits) {
        Double amount = amount(value, max, digits);
        return amount == null || amount <= 0 ? null : amount;
    }

    private static Double amount(Double value, double max, int digits) {
        if (value == null || value.isNaN() || value.isInfinite() || value < 0 || value > max) {
            return null;
        }
        return round(value, digits);
    }

    private static Integer between(Integer value, int min, int max) {
        return value == null || value < min || value > max ? null : value;
    }

    private static double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }

    private static <T> T first(List<T> values, T fallback) {
        if (values == null) {
            return fallback;
        }
        return values.stream().filter(Objects::nonNull).findFirst().orElse(fallback);
    }
}
