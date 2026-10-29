package ru.anyforms.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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

    public static LocalDate requireDate(JsonNode arguments, String name) {
        String value = optionalText(arguments, name);
        if (value == null) {
            throw new IllegalArgumentException("Не передан обязательный параметр " + name);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Параметр " + name + " должен быть датой в формате ГГГГ-ММ-ДД");
        }
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
