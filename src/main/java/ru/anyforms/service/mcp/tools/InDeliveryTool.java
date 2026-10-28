package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpTool;

import java.util.Map;

@Component
@RequiredArgsConstructor
class InDeliveryTool implements McpTool {

    private final CustomProductItemService itemService;
    private final McpCustomOrderReader reader;

    @Override
    public String name() {
        return "list_in_delivery";
    }

    @Override
    public String title() {
        return "В доставке";
    }

    @Override
    public String description() {
        return "Отправленные заказы под заказ, ещё не врученные клиенту: заказ с трекером и статусом СДЭК "
                + "+ позиции в статусе DELIVERING (без ссылок на файлы).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return NO_ARGUMENTS;
    }

    @Override
    public Object call(JsonNode arguments) {
        return reader.groupsWithoutFileLinks(itemService.getInDeliveryGroups());
    }
}
