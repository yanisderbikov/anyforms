package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.mcp.McpArguments;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpTool;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class GetCustomOrderTool implements McpTool {

    private final McpCustomOrderReader reader;

    @Override
    public String name() {
        return "get_custom_order";
    }

    @Override
    public String title() {
        return "Заказ со всеми позициями";
    }

    @Override
    public String description() {
        return "Заказ по нашему id и все его позиции под заказ в любом статусе, включая отправленные и завершённые. "
                + "В позициях есть временные ссылки на файлы.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("orderId", Map.of("type", "integer", "description", "Наш id заказа (не id сделки amoCRM)")),
                "required", List.of("orderId"));
    }

    @Override
    public Object call(JsonNode arguments) {
        return reader.order(McpArguments.requireLong(arguments, "orderId"));
    }
}
