package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.mcp.McpArguments;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpTool;

import java.util.Arrays;
import java.util.Map;

@Component
@RequiredArgsConstructor
class ListCustomItemsTool implements McpTool {

    private final CustomProductItemService itemService;
    private final McpCustomOrderReader reader;

    @Override
    public String name() {
        return "list_custom_items";
    }

    @Override
    public String title() {
        return "Позиции под заказ";
    }

    @Override
    public String description() {
        return "Позиции под заказ по всем заказам. Без status — все, кроме завершённых (COMPLETED); "
                + "со status — только в этом статусе. У позиции: название, описание, количество, кто моделирует, "
                + "статус, ячейка хранения, orderId, клиент. Без ссылок на файлы — их отдаёт get_custom_item.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("status", Map.of(
                        "type", "string",
                        "enum", Arrays.stream(CustomProductStatus.values()).map(Enum::name).toList(),
                        "description", "MODELING — моделирование, IN_PRODUCTION — в производстве, "
                                + "READY_TO_SHIP — готов к отправке, DELIVERING — доставляется, COMPLETED — завершён")));
    }

    @Override
    public Object call(JsonNode arguments) {
        String status = McpArguments.optionalText(arguments, "status");
        if (status == null) {
            return reader.withoutFileLinks(itemService.getAll());
        }
        return reader.withoutFileLinks(itemService.getAllByStatus(parseStatus(status)));
    }

    private CustomProductStatus parseStatus(String status) {
        try {
            return CustomProductStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неизвестный статус " + status + ", допустимые: "
                    + Arrays.toString(CustomProductStatus.values()));
        }
    }
}
