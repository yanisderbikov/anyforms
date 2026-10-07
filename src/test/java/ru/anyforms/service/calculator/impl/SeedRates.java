package ru.anyforms.service.calculator.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.anyforms.dto.calculator.CalculatorRates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class SeedRates {

    static final String MIGRATION = "db/migration/V69__order_calculator.sql";
    private static final String MARKER = "$rates$";

    private SeedRates() {
    }

    static String json() {
        try (InputStream in = SeedRates.class.getClassLoader().getResourceAsStream(MIGRATION)) {
            if (in == null) {
                throw new IllegalStateException("Нет миграции " + MIGRATION);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int start = sql.indexOf(MARKER) + MARKER.length();
            int end = sql.indexOf(MARKER, start);
            return sql.substring(start, end);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static CalculatorRates load() {
        try {
            return new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .readValue(json(), CalculatorRates.class);
        } catch (IOException e) {
            throw new IllegalStateException("Сид ставок не читается в CalculatorRates", e);
        }
    }
}
