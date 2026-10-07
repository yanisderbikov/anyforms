package ru.anyforms.integration.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.anyforms.dto.calculator.CalculationVariantRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiReference;
import ru.anyforms.dto.calculator.ai.CalculationAiRequest;
import ru.anyforms.dto.calculator.ai.CalculationAiSuggestion;
import ru.anyforms.exception.CalculationAiException;
import ru.anyforms.integration.CalculationAiGateway;
import ru.anyforms.model.calculator.FormType;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "calculator.ai.provider", havingValue = "openai")
class OpenAiCalculationGateway implements CalculationAiGateway {

    static final String PROVIDER = "openai";

    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_IMAGES = 6;
    private static final Duration IMAGE_TIMEOUT = Duration.ofSeconds(30);
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif");

    private final ObjectMapper objectMapper;
    private final String model;
    private final String reasoningEffort;
    private final Duration timeout;
    private final boolean inlineImages;
    private final WebClient api;
    private final WebClient files;
    private final ObjectNode responseSchema;
    private final String systemPrompt;

    OpenAiCalculationGateway(ObjectMapper objectMapper,
                             @Value("${calculator.ai.openai.api-key:}") String apiKey,
                             @Value("${calculator.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                             @Value("${calculator.ai.openai.model:gpt-6.1-sol}") String model,
                             @Value("${calculator.ai.openai.reasoning-effort:}") String reasoningEffort,
                             @Value("${calculator.ai.openai.timeout-seconds:120}") long timeoutSeconds,
                             @Value("${calculator.ai.openai.inline-images:true}") boolean inlineImages) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("CALCULATOR_AI_PROVIDER=openai, но OPENAI_API_KEY не задан");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("CALCULATOR_AI_PROVIDER=openai, но OPENAI_MODEL пустой");
        }
        this.objectMapper = objectMapper;
        this.model = model.trim();
        this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        this.timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 10));
        this.inlineImages = inlineImages;
        this.api = WebClient.builder()
                .baseUrl(baseUrl.trim().replaceAll("/+$", ""))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
        this.files = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IMAGE_BYTES))
                .build();
        this.responseSchema = buildResponseSchema();
        this.systemPrompt = buildSystemPrompt();
        log.info("Калькулятор: AI-подсказки через OpenAI-совместимый API {} (модель {})", baseUrl, this.model);
    }

    @Override
    public String providerName() {
        return PROVIDER + ":" + model;
    }

    @Override
    public CalculationAiSuggestion suggestParameters(CalculationAiRequest request) {
        Attachments attachments = attachments(request.references());
        String body = write(buildRequest(request, attachments));
        long startedAt = System.currentTimeMillis();
        String response;
        try {
            response = api.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
        } catch (WebClientResponseException e) {
            String reason = errorMessage(e.getResponseBodyAsString(), e.getStatusText());
            log.error("OpenAI: HTTP {} — {}", e.getStatusCode().value(), reason);
            throw new CalculationAiException("OpenAI ответил " + e.getStatusCode().value() + ": " + reason);
        } catch (Exception e) {
            log.error("OpenAI: запрос не выполнен", e);
            throw new CalculationAiException("OpenAI недоступен: " + rootMessage(e));
        }
        if (response == null || response.isBlank()) {
            throw new CalculationAiException("OpenAI вернул пустой ответ");
        }
        CalculationAiSuggestion suggestion = parseResponse(response);
        log.info("OpenAI: подсказка для «{}» за {} мс, изображений {}, пропущено файлов {}",
                request.productName(), System.currentTimeMillis() - startedAt,
                attachments.images().size(), attachments.skipped().size());
        return suggestion;
    }

    ObjectNode buildRequest(CalculationAiRequest request, Attachments attachments) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", userText(request, attachments));
        for (String image : attachments.images()) {
            content.addObject()
                    .put("type", "image_url")
                    .putObject("image_url")
                    .put("url", image);
        }
        ObjectNode format = root.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode jsonSchema = format.putObject("json_schema");
        jsonSchema.put("name", "calculation_suggestion");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", responseSchema.deepCopy());
        if (!reasoningEffort.isEmpty()) {
            root.put("reasoning_effort", reasoningEffort);
        }
        return root;
    }

    CalculationAiSuggestion parseResponse(String response) {
        JsonNode root = read(response, "OpenAI вернул не JSON");
        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new CalculationAiException("OpenAI вернул ответ без вариантов");
        }
        JsonNode message = choice.path("message");
        if (message.hasNonNull("refusal")) {
            throw new CalculationAiException("OpenAI отказался отвечать: " + message.get("refusal").asText());
        }
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new CalculationAiException("Ответ OpenAI обрезан по лимиту токенов — попробуйте меньше файлов");
        }
        String content = message.path("content").asText("");
        if (content.isBlank()) {
            throw new CalculationAiException("OpenAI вернул пустое сообщение");
        }
        JsonNode json = read(stripCodeFence(content), "OpenAI ответил не по схеме");
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            log.info("OpenAI: модель {}, токены вход {} / выход {}", root.path("model").asText(model),
                    usage.path("prompt_tokens").asText("?"), usage.path("completion_tokens").asText("?"));
        }
        return CalculationAiSuggestion.builder()
                .widthMm(number(json, "widthMm"))
                .depthMm(number(json, "depthMm"))
                .heightMm(number(json, "heightMm"))
                .variant(CalculationVariantRequest.builder()
                        .formType(formType(json.path("formType").asText(null)))
                        .setFormsCount(integer(json, "setFormsCount"))
                        .cellsCount(integer(json, "cellsCount"))
                        .shellRequired(bool(json, "shellRequired"))
                        .shellUnstable(bool(json, "shellUnstable"))
                        .hasCut(bool(json, "hasCut"))
                        .textureFactor(number(json, "textureFactor"))
                        .slaAreaCm2(number(json, "slaAreaCm2"))
                        .slaVolumeCm3(number(json, "slaVolumeCm3"))
                        .processingHours(number(json, "processingHours"))
                        .fdmProjectHours(number(json, "fdmProjectHours"))
                        .build())
                .notes(notes(json.path("notes")))
                .summary(json.path("summary").asText(null))
                .provider(PROVIDER + ":" + root.path("model").asText(model))
                .build();
    }

    Attachments attachments(List<CalculationAiReference> references) {
        List<String> images = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (references == null) {
            return new Attachments(images, skipped);
        }
        for (CalculationAiReference reference : references) {
            if (reference == null || reference.url() == null) {
                continue;
            }
            String filename = reference.filename() == null ? "файл" : reference.filename();
            String mediaType = IMAGE_TYPES.get(extension(filename));
            if (mediaType == null) {
                skipped.add(filename);
                continue;
            }
            if (images.size() >= MAX_IMAGES) {
                skipped.add(filename + " (больше " + MAX_IMAGES + " изображений)");
                continue;
            }
            if (!inlineImages) {
                images.add(reference.url());
                continue;
            }
            try {
                images.add(dataUrl(reference.url(), mediaType));
            } catch (Exception e) {
                log.warn("OpenAI: не удалось скачать референс {}: {}", filename, rootMessage(e));
                skipped.add(filename + " (не скачался)");
            }
        }
        return new Attachments(images, skipped);
    }

    private String dataUrl(String url, String fallbackMediaType) {
        ResponseEntity<byte[]> file = files.get()
                .uri(URI.create(url))
                .retrieve()
                .toEntity(byte[].class)
                .timeout(IMAGE_TIMEOUT)
                .block();
        if (file == null || file.getBody() == null || file.getBody().length == 0) {
            throw new CalculationAiException("пустой файл");
        }
        MediaType contentType = file.getHeaders().getContentType();
        String mediaType = contentType != null && "image".equals(contentType.getType())
                && IMAGE_TYPES.containsValue(contentType.getType() + "/" + contentType.getSubtype())
                ? contentType.getType() + "/" + contentType.getSubtype()
                : fallbackMediaType;
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(file.getBody());
    }

    private String userText(CalculationAiRequest request, Attachments attachments) {
        List<String> lines = new ArrayList<>();
        lines.add("Изделие: " + orDash(request.productName()));
        lines.add("Что рассказал клиент: " + orDash(request.description()));
        boolean dims = request.widthMm() != null && request.depthMm() != null && request.heightMm() != null
                && request.widthMm() > 0 && request.depthMm() > 0 && request.heightMm() > 0;
        lines.add("Габариты от менеджера (Ш × Г × В): " + (dims
                ? number(request.widthMm()) + " × " + number(request.depthMm()) + " × " + number(request.heightMm()) + " мм"
                : "не указаны"));
        lines.add("Материал заливки: " + (request.pourMaterial() == null ? "—" : request.pourMaterial().getLabel()));
        lines.add("Тип формы, который выбрал менеджер: "
                + (request.formType() == null ? "—" : request.formType().name() + " (" + request.formType().getLabel() + ")"));
        lines.add("Мастер-модель: " + (request.masterType() == null ? "—" : request.masterType().getLabel()));
        lines.add("Силикон: " + (request.silicone() == null ? "—" : request.silicone().getLabel()));
        lines.add("Тираж: " + (request.tirage() == null ? "не указан" : request.tirage() + " шт"));
        lines.add("Изображения-референсы: " + (attachments.images().isEmpty()
                ? "нет"
                : attachments.images().size() + " шт, приложены ниже"));
        if (!attachments.skipped().isEmpty()) {
            lines.add("Другие файлы клиента (тебе недоступны, учитывай только названия): "
                    + String.join(", ", attachments.skipped()));
        }
        return String.join("\n", lines);
    }

    private String buildSystemPrompt() {
        String formTypes = Arrays.stream(FormType.values())
                .map(t -> "- " + t.name() + " — " + t.getLabel() + ". " + t.getHint())
                .collect(Collectors.joining("\n"));
        return """
                Ты — технолог-расчётчик мастерской AnyForms. Мастерская делает силиконовые формы (молды) по изделию \
                клиента: мастер-модель печатается на SLA-принтере (иногда на FDM из ABS), вокруг неё в пластиковой \
                литьевой оснастке отливается форма из платинового или оловянного силикона.

                По описанию и референсам оцени параметры изделия и формы для калькулятора цены. Пиши по-русски.

                Правила:
                - Заполняй только то, что можно обоснованно оценить; остальное — null. Не выдумывай.
                - Габариты, которые указал менеджер, считай верными. Если масштаба на референсах нет и в описании \
                размеров нет — габариты null.
                - Веса силикона и оснастки не считай: калькулятор посчитает их сам по площади, объёму и габаритам.

                Поля ответа:
                - widthMm, depthMm, heightMm — габариты изделия, мм.
                - slaAreaCm2 — площадь поверхности изделия (всех частей), см²; slaVolumeCm3 — объём изделия, см³. \
                Если 3D-модели нет, приблизь изделие простыми телами (шар, цилиндр, параллелепипед, эллипсоид, конус) \
                и посчитай. Мелкие детали увеличивают площадь.
                - textureFactor — 1 для гладкой поверхности и мелких деталей, 1,5–2 для крупной фактуры (рис, вязка, мех).
                - formType — подходящий тип формы из списка ниже; setFormsCount — форм в комплекте (тип SET); \
                cellsCount — ячеек в плоской матрице.
                - shellRequired — нужен ли кожух (рабочая опора) к форме; shellUnstable — узкое или круглое сечение \
                либо форма стоит «вверх дном» на вершине, кожуху нужно основание.
                - hasCut — нужен ли продольный разрез формы (поднутрения, сложный рельеф, высокое узкое изделие).
                - processingHours — часы обработки мастер-модели (поддержки, шлифовка, сборка частей). \
                Ориентиры: свеча «Онигири» 80×45×80 мм — 1 ч; матрица саше на 16 ячеек — 2,25 ч.
                - fdmProjectHours — часы проектирования литьевой оснастки. Ориентиры: «Онигири» — 1 ч, \
                матрица на 16 ячеек — 3 ч.
                - notes — по каждому заполненному полю: откуда значение и какие допущения.
                - summary — 1–3 предложения: что за изделие, главные допущения, что уточнить у клиента.

                Типы форм:
                """ + formTypes;
    }

    private ObjectNode buildResponseSchema() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("summary", type("string"));
        properties.set("widthMm", nullable("number"));
        properties.set("depthMm", nullable("number"));
        properties.set("heightMm", nullable("number"));
        ObjectNode formType = nullable("string");
        ArrayNode formTypes = formType.putArray("enum");
        Arrays.stream(FormType.values()).forEach(t -> formTypes.add(t.name()));
        formTypes.addNull();
        properties.set("formType", formType);
        properties.set("setFormsCount", nullable("integer"));
        properties.set("cellsCount", nullable("integer"));
        properties.set("shellRequired", nullable("boolean"));
        properties.set("shellUnstable", nullable("boolean"));
        properties.set("hasCut", nullable("boolean"));
        properties.set("textureFactor", nullable("number"));
        properties.set("slaAreaCm2", nullable("number"));
        properties.set("slaVolumeCm3", nullable("number"));
        properties.set("processingHours", nullable("number"));
        properties.set("fdmProjectHours", nullable("number"));
        ObjectNode note = objectMapper.createObjectNode();
        note.set("field", type("string"));
        note.set("note", type("string"));
        ObjectNode notes = type("array");
        notes.set("items", strictObject(note));
        properties.set("notes", notes);
        return strictObject(properties);
    }

    private ObjectNode strictObject(ObjectNode properties) {
        ObjectNode node = type("object");
        node.set("properties", properties);
        ArrayNode required = node.putArray("required");
        properties.fieldNames().forEachRemaining(required::add);
        node.put("additionalProperties", false);
        return node;
    }

    private ObjectNode type(String type) {
        return objectMapper.createObjectNode().put("type", type);
    }

    private ObjectNode nullable(String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.putArray("type").add(type).add("null");
        return node;
    }

    private Map<String, String> notes(JsonNode notes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (notes.isArray()) {
            notes.forEach(note -> {
                String field = note.path("field").asText("");
                String text = note.path("note").asText("");
                if (!field.isBlank() && !text.isBlank()) {
                    result.merge(field, text, (a, b) -> a + " " + b);
                }
            });
        }
        return result;
    }

    private String errorMessage(String body, String fallback) {
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText("");
            return message.isBlank() ? fallback : message;
        } catch (Exception e) {
            return fallback;
        }
    }

    private JsonNode read(String json, String error) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new CalculationAiException(error);
        }
    }

    private String write(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new CalculationAiException("Не удалось собрать запрос к OpenAI");
        }
    }

    private static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static Double number(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private static Integer integer(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value != null && value.isNumber() ? (int) Math.round(value.asDouble()) : null;
    }

    private static Boolean bool(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private static FormType formType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FormType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String number(Double value) {
        return value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    record Attachments(List<String> images, List<String> skipped) {
    }
}
