package ru.anyforms.service.mcp.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.service.mcp.McpArguments;
import ru.anyforms.service.mcp.McpTool;
import ru.anyforms.service.mcp.McpToolRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpRequestHandlerImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpRequestHandlerImpl handler = new McpRequestHandlerImpl(
            new McpToolRegistry(List.of(new EchoOrderTool())), objectMapper);

    private JsonNode send(String json) throws Exception {
        return handler.handle(objectMapper.readTree(json));
    }

    @Test
    void initializeAgreesOnRequestedVersionAndAnnouncesTools() throws Exception {
        JsonNode response = send("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},
                 "clientInfo":{"name":"claude-code","version":"2"}}}""");

        assertEquals(1, response.get("id").asInt());
        assertEquals("2025-06-18", response.at("/result/protocolVersion").asText());
        assertFalse(response.at("/result/capabilities/tools").isMissingNode());
        assertEquals("anyforms", response.at("/result/serverInfo/name").asText());
    }

    @Test
    void unknownProtocolVersionFallsBackToLatestSupported() throws Exception {
        JsonNode response = send("""
                {"jsonrpc":"2.0","id":"a","method":"initialize","params":{"protocolVersion":"2099-01-01"}}""");

        assertEquals(McpRequestHandlerImpl.SUPPORTED_PROTOCOL_VERSIONS.get(0),
                response.at("/result/protocolVersion").asText());
        assertEquals("a", response.get("id").asText());
    }

    @Test
    void notificationGetsNoResponse() throws Exception {
        assertNull(send("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}"""));
    }

    @Test
    void toolsAreListedAsReadOnly() throws Exception {
        JsonNode tool = send("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}""").at("/result/tools/0");

        assertEquals("get_order", tool.get("name").asText());
        assertEquals("object", tool.at("/inputSchema/type").asText());
        assertTrue(tool.at("/annotations/readOnlyHint").asBoolean());
        assertFalse(tool.at("/annotations/destructiveHint").asBoolean(true));
    }

    @Test
    void toolCallReturnsJsonAsText() throws Exception {
        JsonNode result = send("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_order","arguments":{"orderId":42}}}""")
                .get("result");

        assertFalse(result.get("isError").asBoolean());
        assertEquals("text", result.at("/content/0/type").asText());
        assertEquals(42, objectMapper.readTree(result.at("/content/0/text").asText()).get("orderId").asInt());
    }

    @Test
    void toolFailuresComeBackAsToolErrorsForTheModel() throws Exception {
        JsonNode notFound = send("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_order","arguments":{"orderId":404}}}""")
                .get("result");
        assertTrue(notFound.get("isError").asBoolean());
        assertEquals("Заказ не найден: 404", notFound.at("/content/0/text").asText());

        JsonNode missingArg = send("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_order","arguments":{}}}""")
                .get("result");
        assertTrue(missingArg.get("isError").asBoolean());
    }

    @Test
    void unknownToolAndMethodAreProtocolErrors() throws Exception {
        assertEquals(-32602, send("""
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"delete_order"}}""")
                .at("/error/code").asInt());
        assertEquals(-32601, send("""
                {"jsonrpc":"2.0","id":7,"method":"resources/list"}""").at("/error/code").asInt());
    }

    @Test
    void malformedRequestGetsInvalidRequestWithNullId() throws Exception {
        JsonNode response = send("""
                {"id":8,"method":"ping"}""");

        assertEquals(-32600, response.at("/error/code").asInt());
        assertEquals(8, response.get("id").asInt());

        JsonNode noId = handler.handle(objectMapper.readTree("42"));
        assertTrue(noId.has("id"));
        assertTrue(noId.get("id").isNull());
    }

    @Test
    void batchAnswersOnlyRequestsWithId() throws Exception {
        JsonNode response = send("""
                [{"jsonrpc":"2.0","method":"notifications/initialized"},
                 {"jsonrpc":"2.0","id":9,"method":"ping"},
                 {"jsonrpc":"2.0","id":10,"result":{}}]""");

        assertTrue(response.isArray());
        assertEquals(1, response.size());
        assertEquals(9, response.get(0).get("id").asInt());
    }

    private static final class EchoOrderTool implements McpTool {

        @Override
        public String name() {
            return "get_order";
        }

        @Override
        public String title() {
            return "Заказ";
        }

        @Override
        public String description() {
            return "Тестовый инструмент";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of("orderId", Map.of("type", "integer")),
                    "required", List.of("orderId"));
        }

        @Override
        public Object call(JsonNode arguments) {
            long orderId = McpArguments.requireLong(arguments, "orderId");
            if (orderId == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: 404");
            }
            return Map.of("orderId", orderId);
        }
    }
}
