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
class ReadyToShipTool implements McpTool {

    private final CustomProductItemService itemService;
    private final McpCustomOrderReader reader;

    @Override
    public String name() {
        return "list_ready_to_ship";
    }

    @Override
    public String title() {
        return "Готово к отправке";
    }

    @Override
    public String description() {
        return "Заказы, у которых есть позиции в статусе READY_TO_SHIP, сгруппированные по заказу: "
                + "заказ с данными клиента и ПВЗ + готовые позиции (без ссылок на файлы).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return NO_ARGUMENTS;
    }

    @Override
    public Object call(JsonNode arguments) {
        return reader.groupsWithoutFileLinks(itemService.getReadyToShipGroups());
    }
}
