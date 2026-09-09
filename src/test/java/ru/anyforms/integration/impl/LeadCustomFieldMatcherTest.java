package ru.anyforms.integration.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.model.amo.LeadFilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сравнение поля сделки с фильтром: чекбокс «Розница», списки, отсутствие поля. */
class LeadCustomFieldMatcherTest {

    private static final long RETAIL = AmoCrmFieldId.RETAIL.getId();

    private static JsonObject lead(String customFieldsJson) {
        String json = "{\"id\": 1" + (customFieldsJson == null ? "" : ", \"custom_fields_values\": " + customFieldsJson) + "}";
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static final JsonObject RETAIL_CHECKED = lead(
            "[{\"field_id\": " + RETAIL + ", \"field_type\": \"checkbox\", \"values\": [{\"value\": true}]}]");
    private static final JsonObject RETAIL_UNCHECKED = lead(
            "[{\"field_id\": " + RETAIL + ", \"field_type\": \"checkbox\", \"values\": [{\"value\": false}]}]");
    private static final JsonObject NO_FIELDS = lead(null);
    private static final JsonObject OTHER_FIELD_ONLY = lead(
            "[{\"field_id\": 777, \"values\": [{\"value\": \"x\"}]}]");

    @Test
    void retailTrue_matchesOnlyCheckedLeads() {
        LeadFilter onlyRetail = LeadFilter.forManualRun(null, true);

        assertTrue(LeadCustomFieldMatcher.matches(RETAIL_CHECKED, onlyRetail));
        assertFalse(LeadCustomFieldMatcher.matches(RETAIL_UNCHECKED, onlyRetail));
        assertFalse(LeadCustomFieldMatcher.matches(NO_FIELDS, onlyRetail));
        assertFalse(LeadCustomFieldMatcher.matches(OTHER_FIELD_ONLY, onlyRetail));
    }

    @Test
    void retailFalse_matchesUncheckedAndMissingField() {
        LeadFilter notRetail = LeadFilter.forManualRun(null, false);

        assertFalse(LeadCustomFieldMatcher.matches(RETAIL_CHECKED, notRetail));
        assertTrue(LeadCustomFieldMatcher.matches(RETAIL_UNCHECKED, notRetail));
        assertTrue(LeadCustomFieldMatcher.matches(NO_FIELDS, notRetail));
        assertTrue(LeadCustomFieldMatcher.matches(OTHER_FIELD_ONLY, notRetail));
    }

    @Test
    void noFieldFilter_matchesEverything() {
        assertTrue(LeadCustomFieldMatcher.matches(NO_FIELDS, LeadFilter.NONE));
        assertTrue(LeadCustomFieldMatcher.matches(RETAIL_CHECKED, LeadFilter.byTag("лошадка")));
    }

    @Test
    void selectField_matchesByValueEnumCodeOrEnumId_caseInsensitive() {
        JsonObject lead = lead("[{\"field_id\": 555, \"field_type\": \"select\", "
                + "\"values\": [{\"value\": \"Розница\", \"enum_id\": 900123, \"enum_code\": \"RETAIL\"}]}]");

        assertTrue(LeadCustomFieldMatcher.matches(lead, new LeadFilter(null, 555L, "розница")));
        assertTrue(LeadCustomFieldMatcher.matches(lead, new LeadFilter(null, 555L, "retail")));
        assertTrue(LeadCustomFieldMatcher.matches(lead, new LeadFilter(null, 555L, "900123")));
        assertFalse(LeadCustomFieldMatcher.matches(lead, new LeadFilter(null, 555L, "под заказ")));
    }

    /** Если чекбокс вдруг приходит строкой/числом — «1», «да» тоже считаем отмеченным. */
    @Test
    void truthyStringsCountAsChecked() {
        LeadFilter onlyRetail = LeadFilter.forManualRun(null, true);
        assertTrue(LeadCustomFieldMatcher.matches(
                lead("[{\"field_id\": " + RETAIL + ", \"values\": [{\"value\": \"1\"}]}]"), onlyRetail));
        assertTrue(LeadCustomFieldMatcher.matches(
                lead("[{\"field_id\": " + RETAIL + ", \"values\": [{\"value\": \"Да\"}]}]"), onlyRetail));
        assertFalse(LeadCustomFieldMatcher.matches(
                lead("[{\"field_id\": " + RETAIL + ", \"values\": [{\"value\": \"0\"}]}]"), onlyRetail));
    }
}
