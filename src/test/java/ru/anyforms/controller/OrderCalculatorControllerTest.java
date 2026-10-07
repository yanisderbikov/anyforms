package ru.anyforms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.anyforms.dto.calculator.OrderCalculationDTO;
import ru.anyforms.dto.calculator.OrderCalculationRequest;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;
import ru.anyforms.service.calculator.CalculatorAiService;
import ru.anyforms.service.calculator.CalculatorRatesService;
import ru.anyforms.service.calculator.CalculatorReferenceService;
import ru.anyforms.service.calculator.OrderCalculationJournalService;
import ru.anyforms.service.calculator.OrderCalculatorService;
import ru.anyforms.service.calculator.RatesSnapshot;
import ru.anyforms.service.calculator.impl.CalculatorTestSupport;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderCalculatorControllerTest {

    private static final Principal MANAGER = () -> "manager@anyforms.ru";
    private static final Principal FOUNDER = () -> "founder@anyforms.ru";

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final CalculatorRatesService ratesService = mock(CalculatorRatesService.class);
    private final CalculatorAiService aiService = mock(CalculatorAiService.class);
    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private final OrderCalculatorService calculatorService = CalculatorTestSupport.orderCalculator(ratesService, aiService);
    private final OrderCalculationJournalService journalService = mock(OrderCalculationJournalService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(ratesService.current()).thenReturn(new RatesSnapshot(7L, CalculatorTestSupport.seedRates(), Instant.now(), "seed"));
        when(userAccessService.resolve("manager@anyforms.ru")).thenReturn(Optional.of(
                new UserAccess("manager@anyforms.ru", "Менеджер", Role.SALES_MANAGER, false, null, null)));
        when(userAccessService.resolve("founder@anyforms.ru")).thenReturn(Optional.of(
                new UserAccess("founder@anyforms.ru", "Основатель", Role.ADMIN, false, null, null)));
        OrderCalculatorController controller = new OrderCalculatorController(calculatorService, ratesService,
                journalService, mock(CalculatorReferenceService.class), aiService,
                userAccessService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private String onigiriJson(String extraVariantFields) {
        return """
                {"client":"Анкор ЖБИ","positions":[{"productName":"Свеча Онигири","widthMm":80,"depthMm":45,"heightMm":80,
                "pourMaterial":"WAX","tirages":[5,10],"silicones":["PLATINUM"],"variants":[{"formType":"STOCKING",
                "modelContractorPrice":3000,"slaAreaCm2":273,"slaVolumeCm3":127,"slaHours":8,"fdmProjectHours":1,
                "kitGrams":100,"kitHours":4,"processingHours":1,"prepRub":1500,"tinGrams":205,"copyGrams":133,
                "siliconeGrams":146,"shellGrams":133%s}]}],"discount":{"formsPercent":null}}
                """.formatted(extraVariantFields);
    }

    @Test
    void calculateReturnsKpNumbersInJson() throws Exception {
        MvcResult result = mvc.perform(post("/api/order-calculator/calculate").principal(FOUNDER)
                        .contentType(MediaType.APPLICATION_JSON).content(onigiriJson("")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode position = json.path("positions").get(0);
        assertEquals(28120, position.path("options").get(0).path("kp").path("total").asDouble(), 0.001);
        assertEquals(40730, position.path("options").get(1).path("kp").path("total").asDouble(), 0.001);
        assertEquals(17566.02, position.path("options").get(0).path("price").path("development").asDouble(), 0.001);
        assertEquals("DEFAULT", position.path("options").get(0).path("sources").path("needsIntermediate").asText());
        assertFalse(position.path("options").get(0).path("sources").has("prepRub"));
        assertEquals(0, position.path("selectedOption").asInt());
        assertFalse(position.has("selected"));
        assertEquals(7, json.path("ratesVersionId").asLong());
        assertTrue(json.path("summary").has("maxDiscountShare"));
    }

    @Test
    void managerGetsPricesAndMarginButNoBreakdown() throws Exception {
        MvcResult result = mvc.perform(post("/api/order-calculator/calculate").principal(MANAGER)
                        .contentType(MediaType.APPLICATION_JSON).content(onigiriJson("")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode option : json.path("positions").get(0).path("options")) {
            assertTrue(option.path("price").isNull());
            assertTrue(option.path("cost").isNull());
            assertTrue(option.path("kp").path("total").asDouble() > 0);
            assertTrue(option.path("margin").isNumber());
        }
        JsonNode first = json.path("positions").get(0).path("options").get(0);
        assertEquals(28120, first.path("kp").path("total").asDouble(), 0.001);
        assertTrue(first.path("developmentWorks").toString().contains("intermediate"));
        assertTrue(json.path("summary").path("margin").isNumber());
        assertFalse(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("formCalc"));
    }

    @Test
    void journalEntryHidesBreakdownFromManager() throws Exception {
        JsonNode stored = objectMapper.readTree(
                "{\"positions\":[{\"options\":[{\"kp\":{\"total\":28120},\"price\":{\"formCalc\":2107.08},"
                        + "\"cost\":{\"total\":14404},\"margin\":0.3878}]}],\"summary\":{\"margin\":0.3878}}");
        when(journalService.get(5L)).thenReturn(new OrderCalculationDTO(null, objectMapper.createObjectNode(), stored, Map.of()));

        JsonNode manager = objectMapper.readTree(mvc.perform(get("/api/order-calculator/calculations/5").principal(MANAGER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode founder = objectMapper.readTree(mvc.perform(get("/api/order-calculator/calculations/5").principal(FOUNDER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        JsonNode managerOption = manager.path("result").path("positions").get(0).path("options").get(0);
        assertTrue(managerOption.path("price").isNull());
        assertTrue(managerOption.path("cost").isNull());
        assertEquals(28120, managerOption.path("kp").path("total").asDouble(), 0.001);
        assertEquals(0.3878, managerOption.path("margin").asDouble(), 0.00001);
        assertEquals(2107.08, founder.path("result").path("positions").get(0).path("options").get(0)
                .path("price").path("formCalc").asDouble(), 0.001);
        assertTrue(stored.path("positions").get(0).path("options").get(0).has("cost"));
    }

    @Test
    void validationMessageIsHumanReadable() throws Exception {
        MvcResult result = mvc.perform(post("/api/order-calculator/calculate").principal(MANAGER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positions\":[{\"tirages\":[0],\"variants\":[{\"formType\":\"STOCKING\"}]}]}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("message").asText()
                .contains("Тираж — от 1 формы"));
    }

    @Test
    void unknownEnumIsBadRequest() throws Exception {
        mvc.perform(post("/api/order-calculator/calculate").principal(MANAGER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positions\":[{\"variants\":[{\"formType\":\"PYRAMID\"}]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerGetsForbiddenForFounderExceptions() throws Exception {
        String body = onigiriJson(",\"formPriceOverride\":950");

        MvcResult result = mvc.perform(post("/api/order-calculator/calculate").principal(MANAGER)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andReturn();
        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("основатель"));

        mvc.perform(post("/api/order-calculator/calculate").principal(FOUNDER)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void onlyFounderUpdatesRates() throws Exception {
        String rates = objectMapper.writeValueAsString(CalculatorTestSupport.seedRates());

        mvc.perform(put("/api/order-calculator/rates").principal(MANAGER)
                        .contentType(MediaType.APPLICATION_JSON).content(rates))
                .andExpect(status().isForbidden());
        verify(ratesService, never()).update(any(), any());

        mvc.perform(put("/api/order-calculator/rates").principal(FOUNDER)
                        .contentType(MediaType.APPLICATION_JSON).content(rates))
                .andExpect(status().isOk());
        verify(ratesService).update(any(), any());
    }

    @Test
    void ratesAreHiddenFromManager() throws Exception {
        mvc.perform(get("/api/order-calculator/rates").principal(MANAGER))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/order-calculator/rates/history").principal(MANAGER))
                .andExpect(status().isForbidden());
        verify(ratesService, never()).get();
        verify(ratesService, never()).history(anyInt());

        mvc.perform(get("/api/order-calculator/rates").principal(FOUNDER))
                .andExpect(status().isOk());
        mvc.perform(get("/api/order-calculator/rates/history").principal(FOUNDER))
                .andExpect(status().isOk());
        verify(ratesService).get();
        verify(ratesService).history(anyInt());
    }

    @Test
    void optionsTellFrontWhoIsFounder() throws Exception {
        MvcResult result = mvc.perform(get("/api/order-calculator/options").principal(FOUNDER))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertTrue(json.path("founder").asBoolean());
        assertFalse(json.path("aiAvailable").asBoolean());
        assertEquals("STOCKING", json.path("formTypes").get(0).path("code").asText());
    }

    @Test
    void requestRoundTripsThroughStoredJson() throws Exception {
        OrderCalculationRequest request = objectMapper.readValue(onigiriJson(""), OrderCalculationRequest.class);
        String stored = objectMapper.writeValueAsString(request);

        assertFalse(stored.contains("exceptions"));
        assertEquals(request, objectMapper.readValue(stored, OrderCalculationRequest.class));
    }
}
