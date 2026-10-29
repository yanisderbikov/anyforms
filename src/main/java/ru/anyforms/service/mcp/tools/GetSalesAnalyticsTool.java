package ru.anyforms.service.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.service.mcp.McpArguments;
import ru.anyforms.service.mcp.McpSalesAnalytics;
import ru.anyforms.service.mcp.McpTool;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class GetSalesAnalyticsTool implements McpTool {

    private final McpSalesAnalytics analytics;

    @Override
    public String name() {
        return "get_sales_analytics";
    }

    @Override
    public String title() {
        return "Аналитика продаж";
    }

    @Override
    public String description() {
        return "Продажи магазинов за период: сколько оплаченных заказов, сколько молдов куплено, средний чек и общая сумма "
                + "в рублях — всё уже посчитано на сервере. Учитываются только заказы, оплаченные на витрине "
                + "(возвраты не считаются), дата — момент оплаты, сумма — фактически оплаченные деньги со скидками. "
                + "Без shop — итог по всем магазинам и разбивка по каждому (slug и название магазинов видны в ответе); "
                + "с shop — только этот магазин. У каждого магазина есть разбивка по товарам.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "from", Map.of("type", "string", "format", "date",
                                "description", "Начало периода включительно, ГГГГ-ММ-ДД"),
                        "to", Map.of("type", "string", "format", "date",
                                "description", "Конец периода включительно, ГГГГ-ММ-ДД"),
                        "shop", Map.of("type", "string",
                                "description", "slug магазина, например af_pastry; не передавать — по всем магазинам")),
                "required", List.of("from", "to"));
    }

    @Override
    public Object call(JsonNode arguments) {
        return analytics.sales(McpArguments.requireDate(arguments, "from"), McpArguments.requireDate(arguments, "to"),
                McpArguments.optionalText(arguments, "shop"));
    }
}
