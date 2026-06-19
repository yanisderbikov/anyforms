package ru.anyforms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Конвертация денег между копейками (как храним в БД) и строкой рублей вида {@code "990.00"},
 * которую ожидает Юкасса.
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /** Копейки → строка рублей с двумя знаками, например {@code 99000 → "990.00"}. */
    public static String kopecksToString(long kopecks) {
        return BigDecimal.valueOf(kopecks)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** Строка рублей → копейки, например {@code "990.00" → 99000}. */
    public static long stringToKopecks(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Сумма не может быть пустой");
        }
        return new BigDecimal(value.trim())
                .multiply(BigDecimal.valueOf(100))
                .longValue();
    }
}
