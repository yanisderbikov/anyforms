package ru.anyforms.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public final class McpArguments {

    private McpArguments() {
    }

    public static Long optionalLong(JsonNode arguments, String name) {
        JsonNode value = arguments == null ? null : arguments.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.canConvertToLong() && value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isTextual() && value.asText().trim().matches("\\d+")) {
            return Long.parseLong(value.asText().trim());
        }
        throw new IllegalArgumentException("Параметр " + name + " должен быть целым числом");
    }

    public static long requireLong(JsonNode arguments, String name) {
        Long value = optionalLong(arguments, name);
        if (value == null) {
            throw new IllegalArgumentException("Не передан обязательный параметр " + name);
        }
        return value;
    }

    public static String optionalText(JsonNode arguments, String name) {
        JsonNode value = arguments == null ? null : arguments.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
