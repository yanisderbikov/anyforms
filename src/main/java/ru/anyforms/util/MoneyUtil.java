package ru.anyforms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

    private MoneyUtil() {
    }

    public static String kopecksToString(long kopecks) {
        return BigDecimal.valueOf(kopecks)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    public static long applyDiscountPercent(long kopecks, int discountPercent) {
        return BigDecimal.valueOf(kopecks)
                .multiply(BigDecimal.valueOf(100 - discountPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    public static long stringToKopecks(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Сумма не может быть пустой");
        }
        return new BigDecimal(value.trim())
                .multiply(BigDecimal.valueOf(100))
                .longValue();
    }
}
