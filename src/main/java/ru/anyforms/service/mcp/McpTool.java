package ru.anyforms.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import ru.anyforms.dto.mcp.McpToolDescriptor;

import java.util.Map;

public interface McpTool {

    Map<String, Object> NO_ARGUMENTS = Map.of("type", "object", "properties", Map.of());

    String name();

    String title();

    String description();

    Map<String, Object> inputSchema();

    Object call(JsonNode arguments);

    default boolean readOnly() {
        return true;
    }

    default boolean idempotent() {
        return true;
    }

    default McpToolDescriptor descriptor() {
        return new McpToolDescriptor(name(), title(), description(), inputSchema(),
                Map.of("title", title(), "readOnlyHint", readOnly(), "destructiveHint", false,
                        "idempotentHint", idempotent(), "openWorldHint", false));
    }
}
