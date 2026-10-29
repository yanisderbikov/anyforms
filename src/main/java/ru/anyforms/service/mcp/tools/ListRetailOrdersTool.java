package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.mcp.McpCustomOrderReader;
import ru.anyforms.service.mcp.McpTool;

import java.util.Map;

@Component
@RequiredArgsConstructor
class ListRetailOrdersTool implements McpTool {

    private final McpCustomOrderReader reader;

    @Override
    public String name() {
        return "list_retail_orders";
    }

    @Override
    public String title() {
        return "Розница к отправке";
    }

    @Override
    public String description() {
        return "Оплаченные розничные заказы, которые ещё не отправлены (без трекера), как вкладка «без трекера» в админке, "
                + "от старых к новым по дате оплаты. У каждого: id, publicId, ФИО клиента, purchaseDate — дата оплаты, "
                + "comment — комментарий, items — товары (название, количество), ПВЗ СДЭК, способ получения, "
                + "бесплатная доставка, витрина, промокод. Отправленные заказы сюда не попадают. "
                + "Телефон клиента MCP не отдаёт. Только чтение.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return NO_ARGUMENTS;
    }

    @Override
    public Object call(JsonNode arguments) {
        return reader.retailOrdersWithoutTracker();
    }
}
