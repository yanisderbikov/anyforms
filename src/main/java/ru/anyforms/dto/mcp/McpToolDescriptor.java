package ru.anyforms.dto.mcp;

import java.util.Map;

public record McpToolDescriptor(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> annotations) {
}
