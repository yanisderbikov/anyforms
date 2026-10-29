package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpTool;

import java.util.Map;

@Component
@RequiredArgsConstructor
class ListCustomOrdersTool implements McpTool {

    private final McpCustomOrderReader reader;

    @Override
    public String name() {
        return "list_custom_orders";
    }

    @Override
    public String title() {
        return "Заказы под заказ в работе";
    }

    @Override
    public String description() {
        return "Оплаченные заказы под заказ, которые ещё не отправлены (без трекера), от новых к старым. "
                + "У каждого: id, ФИО клиента, ПВЗ СДЭК, дата, комментарий, customItemsCount — сколько позиций. "
                + "Телефон клиента MCP не отдаёт. "
                + "Позиции заказа — get_custom_order.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return NO_ARGUMENTS;
    }

    @Override
    public Object call(JsonNode arguments) {
        return reader.workableOrders();
    }
}
