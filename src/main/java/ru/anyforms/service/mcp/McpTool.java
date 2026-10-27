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

    default McpToolDescriptor descriptor() {
        return new McpToolDescriptor(name(), title(), description(), inputSchema(),
                Map.of("title", title(), "readOnlyHint", true, "destructiveHint", false,
                        "idempotentHint", true, "openWorldHint", false));
    }
}
