package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.mcp.McpArguments;
import ru.anyforms.service.mcp.McpTool;

import java.util.Map;

@Component
@RequiredArgsConstructor
class GetCustomItemTool implements McpTool {

    private final CustomProductItemService itemService;

    @Override
    public String name() {
        return "get_custom_item";
    }

    @Override
    public String title() {
        return "Позиция под заказ";
    }

    @Override
    public String description() {
        return "Одна позиция в любом статусе — по id или по публичному номеру (6 символов A-Z/0-9 из ссылки на позицию). "
                + "Передайте что-то одно. В ответе есть временные ссылки на файлы.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of("type", "integer", "description", "id позиции"),
                        "publicId", Map.of("type", "string", "description", "Публичный номер позиции, например K7Q2ZD")));
    }

    @Override
    public Object call(JsonNode arguments) {
        Long id = McpArguments.optionalLong(arguments, "id");
        String publicId = McpArguments.optionalText(arguments, "publicId");
        if ((id == null) == (publicId == null)) {
            throw new IllegalArgumentException("Передайте ровно один параметр: id или publicId");
        }
        return id != null ? itemService.getById(id) : itemService.getByPublicId(publicId.toUpperCase());
    }
}
