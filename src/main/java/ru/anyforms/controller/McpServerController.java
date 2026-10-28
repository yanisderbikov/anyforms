package ru.anyforms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.mcp.JsonRpcError;
import ru.anyforms.dto.mcp.JsonRpcResponse;
import ru.anyforms.service.mcp.McpRequestHandler;

@RestController
@RequestMapping("/api/mcp/server")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "MCP", description = "API заказов под заказ для MCP")
public class McpServerController {

    private final McpRequestHandler requestHandler;

    @Operation(summary = "MCP-сервер (Streamable HTTP, без сессий)",
            description = "JSON-RPC 2.0: initialize, ping, tools/list, tools/call. Инструменты читают заказы, менять можно только статус позиции и лог производства. "
                    + "Уведомления без id — 202 без тела. Только MCP-токен администратора или токен админки ADMIN")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode message) {
        JsonNode response = requestHandler.handle(message);
        return response == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(response);
    }

    @Operation(summary = "SSE-поток не поддерживается — 405")
    @GetMapping
    public ResponseEntity<Void> stream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<JsonRpcResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(JsonRpcResponse.failure(null, JsonRpcError.PARSE_ERROR, "Тело запроса — не JSON"));
    }
}
