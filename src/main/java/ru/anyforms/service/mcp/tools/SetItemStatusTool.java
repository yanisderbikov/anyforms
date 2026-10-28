package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.model.CustomProductStatus;
import ru.anyforms.service.CustomProductItemService;
import ru.anyforms.service.mcp.McpArguments;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpTool;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class SetItemStatusTool implements McpTool {

    static final List<CustomProductStatus> MANUAL_STATUSES = List.of(
            CustomProductStatus.MODELING, CustomProductStatus.IN_PRODUCTION, CustomProductStatus.READY_TO_SHIP);

    private final CustomProductItemService itemService;
    private final McpCustomOrderReader reader;

    @Override
    public String name() {
        return "set_item_status";
    }

    @Override
    public String title() {
        return "Сменить статус позиции";
    }

    @Override
    public String description() {
        return "Меняет статус позиции под заказ, как селект статуса в админке: MODELING, IN_PRODUCTION или READY_TO_SHIP. "
                + "Работает только для позиций в этих же статусах — отправленные (DELIVERING) и завершённые (COMPLETED) "
                + "через MCP не трогаются, отправка идёт через трекер в админке. "
                + "Смена статуса уходит примечанием в сделку amoCRM от имени того, кто подключил MCP.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "itemId", Map.of("type", "integer", "description", "id позиции"),
                        "status", Map.of(
                                "type", "string",
                                "enum", MANUAL_STATUSES.stream().map(Enum::name).toList(),
                                "description", "MODELING — моделирование, IN_PRODUCTION — в производстве, "
                                        + "READY_TO_SHIP — готов к отправке")),
                "required", List.of("itemId", "status"));
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public Object call(JsonNode arguments) {
        long itemId = McpArguments.requireLong(arguments, "itemId");
        CustomProductStatus status = parseStatus(McpArguments.optionalText(arguments, "status"));
        CustomProductStatus current = itemService.getById(itemId).getStatus();
        if (!MANUAL_STATUSES.contains(current)) {
            throw new IllegalArgumentException("Позиция " + itemId + " уже в статусе " + current
                    + " — через MCP статус меняется только у позиций в работе");
        }
        CustomProductItemDTO updated = itemService.updateStatus(itemId, status);
        return reader.withoutFileLinks(List.of(updated)).get(0);
    }

    private CustomProductStatus parseStatus(String status) {
        if (status == null) {
            throw new IllegalArgumentException("Не передан обязательный параметр status");
        }
        return MANUAL_STATUSES.stream()
                .filter(s -> s.name().equalsIgnoreCase(status))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Статус " + status + " через MCP поставить нельзя, допустимые: "
                        + MANUAL_STATUSES));
    }
}
