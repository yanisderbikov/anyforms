package ru.anyforms.integration.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.anyforms.dto.calculator.ai.CalculationAiReference;
import ru.anyforms.dto.calculator.ai.CalculationAiRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiSuggestion;
import ru.anyforms.exception.CalculationAiException;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.model.calculator.MasterType;
import ru.anyforms.model.calculator.PourMaterial;
import ru.anyforms.model.calculator.SiliconeType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCalculationGatewayTest {

    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10, 0x20, 0x30};
    private static final String SUGGESTION = """
            {"summary":"Свеча в форме онигири, гладкая.","widthMm":80,"depthMm":45,"heightMm":80,
            "formType":"BRICK","setFormsCount":null,"cellsCount":null,"shellRequired":false,
            "shellUnstable":null,"hasCut":true,"textureFactor":1.5,"slaAreaCm2":273,"slaVolumeCm3":127.4,
            "processingHours":1,"fdmProjectHours":null,
            "notes":[{"field":"slaAreaCm2","note":"призма со скруглёнными углами"},
                     {"field":"hasCut","note":"поднутрение у основания"}]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestLine = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> photoQuery = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody;
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestLine.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/bucket/", exchange -> {
            if (!exchange.getRequestURI().getPath().endsWith(".jpg")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            photoQuery.set(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, PHOTO.length);
            exchange.getResponseBody().write(PHOTO);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private OpenAiCalculationGateway gateway(String reasoningEffort, boolean inlineImages) {
        return new OpenAiCalculationGateway(objectMapper, "test-key", base() + "/v1/", "gpt-test",
                reasoningEffort, 10, inlineImages);
    }

    private CalculationAiRequest request(List<CalculationAiReference> references) {
        return CalculationAiRequest.builder()
                .productName("Свеча Онигири")
                .description("Воск, нужно 5 форм")
                .widthMm(80.0)
                .depthMm(45.0)
                .heightMm(80.0)
                .pourMaterial(PourMaterial.WAX)
                .formType(FormType.STOCKING)
                .masterType(MasterType.SLA)
                .silicone(SiliconeType.PLATINUM)
                .tirage(5)
                .references(references)
                .build();
    }

    private List<CalculationAiReference> photoAndModel() {
        return List.of(
                new CalculationAiReference("photo.jpg", base() + "/bucket/photo.jpg?X-Amz-Signature=abc%2Fdef"),
                new CalculationAiReference("model.stl", base() + "/bucket/model.stl"));
    }

    private String completion(String content, String finishReason) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-1");
        root.put("model", "gpt-test-2026");
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.putObject("message").put("role", "assistant").put("content", content).putNull("refusal");
        choice.put("finish_reason", finishReason);
        root.putObject("usage").put("prompt_tokens", 1200).put("completion_tokens", 300);
        return root.toString();
    }

    @Test
    void sendsChatCompletionWithInlinedPhotoAndStrictSchema() throws Exception {
        responseBody = completion(SUGGESTION, "stop");

        CalculationAiSuggestion suggestion = gateway("", true).suggestParameters(request(photoAndModel()));

        assertEquals("POST /v1/chat/completions", requestLine.get());
        assertEquals("Bearer test-key", authorization.get());
        assertEquals("X-Amz-Signature=abc%2Fdef", photoQuery.get());

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("gpt-test", body.path("model").asText());
        assertFalse(body.has("reasoning_effort"));
        assertFalse(body.has("temperature"));
        JsonNode messages = body.path("messages");
        assertEquals("system", messages.get(0).path("role").asText());
        assertTrue(messages.get(0).path("content").asText().contains("STOCKING — Чулок"));
        JsonNode content = messages.get(1).path("content");
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals(2, content.size());
        String text = content.get(0).path("text").asText();
        assertTrue(text.contains("Изделие: Свеча Онигири"));
        assertTrue(text.contains("80 × 45 × 80 мм"));
        assertTrue(text.contains("model.stl"));
        assertEquals("image_url", content.get(1).path("type").asText());
        assertEquals("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(PHOTO),
                content.get(1).path("image_url").path("url").asText());

        JsonNode format = body.path("response_format");
        assertEquals("json_schema", format.path("type").asText());
        assertTrue(format.path("json_schema").path("strict").asBoolean());
        assertStrictSchema(format.path("json_schema").path("schema"), "schema");

        assertEquals(80.0, suggestion.widthMm());
        assertEquals(FormType.BRICK, suggestion.variant().getFormType());
        assertEquals(Boolean.TRUE, suggestion.variant().getHasCut());
        assertEquals(Boolean.FALSE, suggestion.variant().getShellRequired());
        assertNull(suggestion.variant().getShellUnstable());
        assertEquals(1.5, suggestion.variant().getTextureFactor());
        assertEquals(273.0, suggestion.variant().getSlaAreaCm2());
        assertEquals(127.4, suggestion.variant().getSlaVolumeCm3());
        assertNull(suggestion.variant().getFdmProjectHours());
        assertNull(suggestion.variant().getSiliconeGrams());
        assertEquals("поднутрение у основания", suggestion.notes().get("hasCut"));
        assertEquals("Свеча в форме онигири, гладкая.", suggestion.summary());
        assertEquals("openai:gpt-test-2026", suggestion.provider());
    }

    @Test
    void passesReasoningEffortAndImageUrlWhenConfigured() throws Exception {
        responseBody = completion(SUGGESTION, "stop");

        gateway("low", false).suggestParameters(request(photoAndModel()));

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("low", body.path("reasoning_effort").asText());
        assertEquals(base() + "/bucket/photo.jpg?X-Amz-Signature=abc%2Fdef",
                body.path("messages").get(1).path("content").get(1).path("image_url").path("url").asText());
        assertNull(photoQuery.get());
    }

    @Test
    void httpErrorCarriesOpenAiMessage() {
        status = 401;
        responseBody = "{\"error\":{\"message\":\"Incorrect API key provided\",\"type\":\"invalid_request_error\"}}";

        CalculationAiException e = assertThrows(CalculationAiException.class,
                () -> gateway("", true).suggestParameters(request(List.of())));

        assertTrue(e.getMessage().contains("401"));
        assertTrue(e.getMessage().contains("Incorrect API key provided"));
    }

    @Test
    void refusalTruncationAndBrokenJsonAreErrors() {
        OpenAiCalculationGateway gateway = gateway("", true);
        String refusal = "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":null,\"refusal\":\"Не могу помочь\"},"
                + "\"finish_reason\":\"stop\"}]}";

        assertTrue(assertThrows(CalculationAiException.class, () -> gateway.parseResponse(refusal))
                .getMessage().contains("Не могу помочь"));
        assertTrue(assertThrows(CalculationAiException.class,
                () -> gateway.parseResponse(completion("{\"summary\":", "length"))).getMessage().contains("обрезан"));
        assertThrows(CalculationAiException.class, () -> gateway.parseResponse(completion("не json", "stop")));
        assertThrows(CalculationAiException.class, () -> gateway.parseResponse("{\"choices\":[]}"));
    }

    @Test
    void acceptsFencedJsonAndUnknownFormType() {
        String fenced = "```json\n" + SUGGESTION.replace("\"BRICK\"", "\"PYRAMID\"") + "\n```";

        CalculationAiSuggestion suggestion = gateway("", true).parseResponse(completion(fenced, "stop"));

        assertNull(suggestion.variant().getFormType());
        assertEquals(273.0, suggestion.variant().getSlaAreaCm2());
    }

    @Test
    void sendsOnlyFirstSixImagesAndSkipsBrokenOnes() {
        List<CalculationAiReference> references = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            references.add(new CalculationAiReference("photo" + i + ".jpg", base() + "/bucket/photo" + i + ".jpg"));
        }
        references.add(0, new CalculationAiReference("missing.png", base() + "/bucket/missing.png"));
        references.add(new CalculationAiReference("iphone.heic", base() + "/bucket/iphone.heic"));

        OpenAiCalculationGateway.Attachments attachments = gateway("", true).attachments(references);

        assertEquals(6, attachments.images().size());
        assertTrue(attachments.skipped().contains("missing.png (не скачался)"));
        assertTrue(attachments.skipped().contains("iphone.heic"));
        assertTrue(attachments.skipped().stream().anyMatch(s -> s.startsWith("photo6.jpg")));
    }

    @Test
    void refusesToStartWithoutKey() {
        assertThrows(IllegalStateException.class, () -> new OpenAiCalculationGateway(objectMapper, " ",
                base() + "/v1", "gpt-test", "", 10, true));
    }

    private static void assertStrictSchema(JsonNode node, String path) {
        if ("object".equals(node.path("type").asText())) {
            assertFalse(node.path("additionalProperties").asBoolean(true), path + ": additionalProperties");
            Set<String> properties = new HashSet<>();
            node.path("properties").fieldNames().forEachRemaining(properties::add);
            Set<String> required = new HashSet<>();
            node.path("required").forEach(r -> required.add(r.asText()));
            assertEquals(properties, required, path + ": required");
            node.path("properties").fields()
                    .forEachRemaining(e -> assertStrictSchema(e.getValue(), path + "." + e.getKey()));
        }
        if (node.has("items")) {
            assertStrictSchema(node.get("items"), path + "[]");
        }
        if (node.has("enum") && node.path("type").isArray()) {
            JsonNode last = node.path("enum").get(node.path("enum").size() - 1);
            assertTrue(last.isNull(), path + ": nullable enum must contain null");
        }
    }
}
