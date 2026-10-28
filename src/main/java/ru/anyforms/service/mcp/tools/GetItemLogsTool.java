package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.CustomProductLogService;
import ru.anyforms.service.mcp.McpArguments;
import ru.anyforms.service.mcp.McpTool;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class GetItemLogsTool implements McpTool {

    private final CustomProductLogService logService;

    @Override
    public String name() {
        return "get_item_logs";
    }

    @Override
    public String title() {
        return "Лог производства позиции";
    }

    @Override
    public String description() {
        return "Записи лога производства позиции от старых к новым: что и когда происходило с изделием.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("itemId", Map.of("type", "integer", "description", "id позиции")),
                "required", List.of("itemId"));
    }

    @Override
    public Object call(JsonNode arguments) {
        return logService.getByItemId(McpArguments.requireLong(arguments, "itemId"));
    }
}
