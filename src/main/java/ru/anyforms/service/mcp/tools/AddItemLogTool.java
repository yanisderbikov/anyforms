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
class AddItemLogTool implements McpTool {

    static final int MAX_TEXT_LENGTH = 4000;

    private final CustomProductLogService logService;

    @Override
    public String name() {
        return "add_item_log";
    }

    @Override
    public String title() {
        return "Добавить запись в лог производства";
    }

    @Override
    public String description() {
        return "Добавляет запись в лог производства позиции, как поле лога в админке: что сделали или что случилось "
                + "с изделием. Время ставит сервер. Запись дублируется примечанием в сделку amoCRM от имени того, "
                + "кто подключил MCP. Удалить или исправить запись нельзя — текст согласуй с пользователем.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "itemId", Map.of("type", "integer", "description", "id позиции"),
                        "text", Map.of("type", "string", "maxLength", MAX_TEXT_LENGTH,
                                "description", "Текст записи, например «Отлили 20 шт, отдали на покраску»")),
                "required", List.of("itemId", "text"));
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public boolean idempotent() {
        return false;
    }

    @Override
    public Object call(JsonNode arguments) {
        long itemId = McpArguments.requireLong(arguments, "itemId");
        String text = McpArguments.optionalText(arguments, "text");
        if (text == null) {
            throw new IllegalArgumentException("Не передан текст записи");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Слишком длинная запись: максимум " + MAX_TEXT_LENGTH + " символов");
        }
        return logService.add(itemId, text);
    }
}
