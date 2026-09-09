package ru.anyforms.integration.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.anyforms.model.amo.LeadFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Сравнение значения кастомного поля сделки (из JSON ответа {@code GET /api/v4/leads})
 * с ожидаемым из {@link LeadFilter}.
 * <p>
 * Чекбокс в amoCRM: у отмеченного лида {@code values:[{value:true}]}, у неотмеченного поля
 * в {@code custom_fields_values} нет вовсе — поэтому ожидаемое {@code "false"} совпадает и с
 * отсутствием поля. Для списков и текста сравниваем с {@code value}, {@code enum_code} и
 * {@code enum_id} без учёта регистра.
 */
final class LeadCustomFieldMatcher {

    private static final Set<String> TRUTHY = Set.of("true", "1", "yes", "да");
    private static final Set<String> FALSY = Set.of("false", "0", "no", "нет");

    private LeadCustomFieldMatcher() {
    }

    static boolean matches(JsonObject lead, LeadFilter filter) {
        if (!filter.hasField()) {
            return true;
        }
        List<String> actual = fieldValues(lead, filter.fieldId());
        String expected = filter.fieldValue().trim().toLowerCase(Locale.ROOT);
        if (TRUTHY.contains(expected)) {
            return actual.stream().anyMatch(LeadCustomFieldMatcher::isTruthy);
        }
        if (FALSY.contains(expected)) {
            return actual.stream().noneMatch(LeadCustomFieldMatcher::isTruthy);
        }
        return actual.stream().anyMatch(v -> v.equalsIgnoreCase(expected));
    }

    /** Все строковые представления значений поля: value, enum_code, enum_id. Пусто — поля у лида нет. */
    private static List<String> fieldValues(JsonObject lead, Long fieldId) {
        List<String> result = new ArrayList<>();
        if (!lead.has("custom_fields_values") || !lead.get("custom_fields_values").isJsonArray()) {
            return result;
        }
        for (JsonElement fieldElement : lead.getAsJsonArray("custom_fields_values")) {
            if (!fieldElement.isJsonObject()) {
                continue;
            }
            JsonObject field = fieldElement.getAsJsonObject();
            if (!field.has("field_id") || field.get("field_id").isJsonNull()
                    || field.get("field_id").getAsLong() != fieldId) {
                continue;
            }
            if (!field.has("values") || !field.get("values").isJsonArray()) {
                continue;
            }
            JsonArray values = field.getAsJsonArray("values");
            for (JsonElement valueElement : values) {
                if (!valueElement.isJsonObject()) {
                    continue;
                }
                JsonObject value = valueElement.getAsJsonObject();
                for (String key : List.of("value", "enum_code", "enum_id")) {
                    if (value.has(key) && !value.get(key).isJsonNull()) {
                        result.add(value.get(key).getAsString().trim());
                    }
                }
            }
        }
        return result;
    }

    private static boolean isTruthy(String value) {
        return TRUTHY.contains(value.toLowerCase(Locale.ROOT));
    }
}
