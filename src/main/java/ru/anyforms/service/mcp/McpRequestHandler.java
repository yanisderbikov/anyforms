package ru.anyforms.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public interface McpRequestHandler {

    JsonNode handle(JsonNode message);
}
