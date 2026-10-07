package ru.anyforms.service.calculator.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.calculator.AiSuggestionRequest;
import ru.anyforms.dto.calculator.CalculationPositionRequest;
import ru.anyforms.dto.calculator.CalculationReferenceDTO;
import ru.anyforms.dto.calculator.CalculationVariantRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiSuggestion;
import ru.anyforms.exception.CalculationAiException;
import ru.anyforms.integration.CalculationAiGateway;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.service.calculator.CalculatorReferenceService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalculatorAiServiceImplTest {

    private final CalculatorReferenceService referenceService = mock(CalculatorReferenceService.class);
    private final CalculationAiGateway gateway = mock(CalculationAiGateway.class);
    private final CalculatorAiServiceImpl service = new CalculatorAiServiceImpl(Optional.of(gateway), referenceService);

    private static AiSuggestionRequest request() {
        return AiSuggestionRequest.builder()
                .position(CalculationPositionRequest.builder()
                        .productName("Гном")
                        .tirages(List.of(10))
                        .variants(List.of(CalculationVariantRequest.builder().formType(FormType.STOCKING).build()))
                        .references(List.of(
                                new CalculationReferenceDTO("order-calculator/references/a.jpg", "гном.jpg"),
                                new CalculationReferenceDTO("custom-products/1/b.jpg", "чужой.jpg")))
                        .build())
                .variantIndex(0)
                .description("садовый гном из гипса")
                .build();
    }

    @Test
    void withoutProviderAiIsUnavailable() {
        CalculatorAiServiceImpl none = new CalculatorAiServiceImpl(Optional.empty(), referenceService);

        assertFalse(none.available());
        assertNull(none.providerName());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> none.suggest(request()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void passesOnlyOwnReferencesWithSignedUrls() {
        when(referenceService.viewUrls(any())).thenReturn(Map.of("order-calculator/references/a.jpg", "https://s3/a.jpg?sig"));
        when(gateway.suggestParameters(any())).thenReturn(CalculationAiSuggestion.builder().summary("ок").build());
        when(gateway.providerName()).thenReturn("openai:gpt-test");

        CalculationAiSuggestion suggestion = service.suggest(request());

        ArgumentCaptor<CalculationAiRequest> captor = ArgumentCaptor.forClass(CalculationAiRequest.class);
        verify(gateway).suggestParameters(captor.capture());
        CalculationAiRequest sent = captor.getValue();
        assertEquals(1, sent.references().size());
        assertEquals("гном.jpg", sent.references().get(0).filename());
        assertEquals("https://s3/a.jpg?sig", sent.references().get(0).url());
        assertEquals(10, sent.tirage());
        assertEquals("садовый гном из гипса", sent.description());
        assertEquals("openai:gpt-test", suggestion.provider());
    }

    @Test
    void providerErrorBecomesBadGatewayWithReason() {
        when(gateway.suggestParameters(any())).thenThrow(new CalculationAiException("OpenAI ответил 401: Incorrect API key provided"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.suggest(request()));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
        assertEquals("OpenAI ответил 401: Incorrect API key provided", e.getReason());
    }

    @Test
    void suggestionIsSanitizedBeforeReachingTheForm() {
        CalculationAiSuggestion raw = CalculationAiSuggestion.builder()
                .widthMm(-5.0)
                .depthMm(45.04)
                .heightMm(9000.0)
                .variant(CalculationVariantRequest.builder()
                        .formType(FormType.FLAT_MATRIX_CLUSTER)
                        .cellsCount(0)
                        .setFormsCount(3)
                        .textureFactor(5.0)
                        .slaAreaCm2(201.0619)
                        .slaVolumeCm3(0.0)
                        .processingHours(1.234)
                        .siliconeGrams(-10.0)
                        .weightReserve(1.5)
                        .modelPriceOverride(100.0)
                        .kitsOverride(1)
                        .formPriceOverride(950.0)
                        .aiFields(List.of("slaAreaCm2"))
                        .build())
                .build();

        CalculationAiSuggestion clean = CalculatorAiServiceImpl.sanitize(raw, "openai:gpt-test");

        assertNull(clean.widthMm());
        assertEquals(45.0, clean.depthMm());
        assertNull(clean.heightMm());
        CalculationVariantRequest v = clean.variant();
        assertEquals(FormType.FLAT_MATRIX_CLUSTER, v.getFormType());
        assertNull(v.getCellsCount());
        assertEquals(3, v.getSetFormsCount());
        assertEquals(3.0, v.getTextureFactor());
        assertEquals(201.1, v.getSlaAreaCm2());
        assertNull(v.getSlaVolumeCm3());
        assertEquals(1.23, v.getProcessingHours());
        assertNull(v.getSiliconeGrams());
        assertNull(v.getWeightReserve());
        assertNull(v.getModelPriceOverride());
        assertNull(v.getKitsOverride());
        assertNull(v.getFormPriceOverride());
        assertNull(v.getAiFields());
        assertTrue(clean.notes().isEmpty());
        assertEquals("openai:gpt-test", clean.provider());

        CalculationAiSuggestion smooth = CalculatorAiServiceImpl.sanitize(CalculationAiSuggestion.builder()
                .variant(CalculationVariantRequest.builder().textureFactor(0.5).build()).build(), "p");
        assertEquals(1.0, smooth.variant().getTextureFactor());
    }
}
