package ru.anyforms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

    /** Минимальная сумма платежа у провайдеров (1 ₽) — итог со скидками не опускаем ниже. */
    public static final long MIN_PAYABLE_KOPECKS = 100;

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

    /**
     * Полная промо-скидка: сначала процент, затем фиксированная сумма
     * ({@code discountAmountKopecks}, null — нет). Итог не опускается ниже
     * {@link #MIN_PAYABLE_KOPECKS}, кроме случая, когда цена и без фикса была ниже.
     */
    public static long applyPromoDiscount(long kopecks, int discountPercent, Long discountAmountKopecks) {
        long discounted = applyDiscountPercent(kopecks, discountPercent);
        if (discountAmountKopecks == null || discountAmountKopecks <= 0) {
            return discounted;
        }
        return Math.max(discounted - discountAmountKopecks, Math.min(discounted, MIN_PAYABLE_KOPECKS));
    }

    /**
     * Распределяет общую скидку по позициям пропорционально их стоимости методом
     * наибольших остатков: сумма долей равна ровно {@code reduction}, ни одна доля
     * не превышает свою позицию. Пока это возможно, каждой позиции оставляется
     * хотя бы копейка. Требует {@code 0 <= reduction < sum(lineTotals)}.
     */
    public static long[] distributeReduction(long[] lineTotals, long reduction) {
        long subtotal = 0;
        for (long line : lineTotals) {
            subtotal += line;
        }
        if (reduction < 0 || reduction >= subtotal) {
            throw new IllegalArgumentException(
                    "Скидка " + reduction + " должна быть в диапазоне [0, " + subtotal + ")");
        }
        long[] shares = new long[lineTotals.length];
        long[] remainders = new long[lineTotals.length];
        long distributed = 0;
        for (int i = 0; i < lineTotals.length; i++) {
            shares[i] = reduction * lineTotals[i] / subtotal;
            remainders[i] = reduction * lineTotals[i] % subtotal;
            distributed += shares[i];
        }
        // Недораспределённые копейки — по одной позициям с наибольшим остатком,
        // в первую очередь тем, у которых после добавки останется хотя бы копейка.
        for (; distributed < reduction; distributed++) {
            int best = pickLine(lineTotals, shares, remainders, true);
            if (best == -1) {
                best = pickLine(lineTotals, shares, remainders, false);
            }
            shares[best]++;
            remainders[best] = 0;
        }
        return shares;
    }

    /** Индекс позиции с наибольшим остатком, куда влезает ещё копейка скидки; -1, если некуда. */
    private static int pickLine(long[] lineTotals, long[] shares, long[] remainders, boolean keepKopeck) {
        int best = -1;
        for (int i = 0; i < lineTotals.length; i++) {
            long capacity = lineTotals[i] - shares[i];
            if (capacity < (keepKopeck ? 2 : 1)) {
                continue;
            }
            if (best == -1 || remainders[i] > remainders[best]) {
                best = i;
            }
        }
        return best;
    }

    /** «3 000 ₽» или «2 999,50 ₽» — для сообщений пользователю. */
    public static String formatRubles(long kopecks) {
        long rub = kopecks / 100;
        long kop = Math.abs(kopecks % 100);
        String grouped = Long.toString(rub).replaceAll("\\B(?=(\\d{3})+(?!\\d))", " ");
        return kop == 0 ? grouped + " ₽" : grouped + String.format(",%02d ₽", kop);
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
