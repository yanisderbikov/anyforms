package ru.anyforms.service.mcp.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.mcp.JsonRpcError;
import ru.anyforms.dto.mcp.JsonRpcResponse;
import ru.anyforms.service.mcp.McpRequestHandler;
import ru.anyforms.service.mcp.McpTool;
import ru.anyforms.service.mcp.McpToolRegistry;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
class McpRequestHandlerImpl implements McpRequestHandler {

    static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");

    private static final String INSTRUCTIONS = """
            Заказы под заказ anyforms. Заказ (order, наш id) — сделка клиента; \
            внутри него позиции (items) — то, что изготавливаем. Статусы позиции по порядку: \
            MODELING (художник делает 3D-модель), IN_PRODUCTION, READY_TO_SHIP, DELIVERING, COMPLETED. \
            Списки отдают позиции без ссылок на файлы; ссылки на файлы (временные, живут недолго) — \
            в get_custom_item и get_custom_order. Менять можно только статус позиции (set_item_status) \
            и добавлять запись в лог производства (add_item_log); обе правки дублируются примечанием \
            в сделку amoCRM, поэтому перед ними уточняй у пользователя позицию и текст.""";

    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode handle(JsonNode message) {
        if (message == null || message.isMissingNode() || message.isNull()) {
            return failure(null, JsonRpcError.INVALID_REQUEST, "Пустой запрос");
        }
        if (message.isArray()) {
            if (message.isEmpty()) {
                return failure(null, JsonRpcError.INVALID_REQUEST, "Пустой пакет запросов");
            }
            ArrayNode responses = objectMapper.createArrayNode();
            message.forEach(single -> {
                JsonNode response = handleSingle(single);
                if (response != null) {
                    responses.add(response);
                }
            });
            return responses.isEmpty() ? null : responses;
        }
        return handleSingle(message);
    }

    private JsonNode handleSingle(JsonNode message) {
        if (!message.isObject()) {
            return failure(null, JsonRpcError.INVALID_REQUEST, "Ожидается JSON-объект");
        }
        JsonNode id = message.get("id");
        JsonNode method = message.get("method");
        if (method == null && (message.has("result") || message.has("error"))) {
            return null;
        }
        if (!"2.0".equals(message.path("jsonrpc").asText()) || method == null || !method.isTextual()) {
            return failure(id, JsonRpcError.INVALID_REQUEST, "Ожидается JSON-RPC 2.0 с полем method");
        }
        if (id == null) {
            log.debug("MCP-уведомление {}", method.asText());
            return null;
        }
        JsonNode params = message.path("params");
        try {
            return objectMapper.valueToTree(JsonRpcResponse.success(id, dispatch(method.asText(), params)));
        } catch (MethodNotFoundException e) {
            return failure(id, JsonRpcError.METHOD_NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            return failure(id, JsonRpcError.INVALID_PARAMS, e.getMessage());
        }
    }

    private Object dispatch(String method, JsonNode params) {
        return switch (method) {
            case "initialize" -> initialize(params);
            case "ping" -> Map.of();
            case "tools/list" -> Map.of("tools", toolRegistry.descriptors());
            case "tools/call" -> callTool(params);
            default -> throw new MethodNotFoundException("Метод не поддерживается: " + method);
        };
    }

    private Map<String, Object> initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText();
        String version = SUPPORTED_PROTOCOL_VERSIONS.contains(requested) ? requested : SUPPORTED_PROTOCOL_VERSIONS.get(0);
        return Map.of(
                "protocolVersion", version,
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of("name", "anyforms", "title", "anyforms: заказы под заказ", "version", "1.0.0"),
                "instructions", INSTRUCTIONS);
    }

    private Map<String, Object> callTool(JsonNode params) {
        String name = params.path("name").asText();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Не передано имя инструмента");
        }
        McpTool tool = toolRegistry.find(name)
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный инструмент: " + name));
        try {
            Object result = tool.call(params.path("arguments"));
            return toolResult(objectMapper.writeValueAsString(result), false);
        } catch (ResponseStatusException e) {
            return toolResult(e.getReason() == null ? "Ошибка запроса" : e.getReason(), true);
        } catch (IllegalArgumentException e) {
            return toolResult(e.getMessage(), true);
        } catch (JsonProcessingException e) {
            log.error("MCP-инструмент {}: не удалось сериализовать ответ", name, e);
            return toolResult("Не удалось сформировать ответ", true);
        } catch (RuntimeException e) {
            log.error("MCP-инструмент {} упал", name, e);
            return toolResult("Внутренняя ошибка сервера", true);
        }
    }

    private Map<String, Object> toolResult(String text, boolean isError) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", isError);
    }

    private JsonNode failure(JsonNode id, int code, String message) {
        return objectMapper.valueToTree(JsonRpcResponse.failure(id, code, message));
    }

    private static final class MethodNotFoundException extends RuntimeException {
        MethodNotFoundException(String message) {
            super(message);
        }
    }
}
