package ru.anyforms.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyUtilTest {

    @Test
    void applyPromoDiscount_percentOnly() {
        assertEquals(50_00, MoneyUtil.applyPromoDiscount(100_00, 50, null));
        assertEquals(100_00, MoneyUtil.applyPromoDiscount(100_00, 0, null));
    }

    @Test
    void applyPromoDiscount_percentThenAmount() {
        // 10 000 ₽ −50% = 5 000 ₽, затем −3 000 ₽ = 2 000 ₽
        assertEquals(2_000_00, MoneyUtil.applyPromoDiscount(10_000_00, 50, 3_000_00L));
    }

    @Test
    void applyPromoDiscount_amountOnly() {
        assertEquals(5_000_00, MoneyUtil.applyPromoDiscount(10_000_00, 0, 5_000_00L));
    }

    @Test
    void applyPromoDiscount_neverBelowMinPayable() {
        // Фикс больше остатка — итог упирается в 1 ₽, а не в минус.
        assertEquals(MoneyUtil.MIN_PAYABLE_KOPECKS, MoneyUtil.applyPromoDiscount(3_000_00, 50, 5_000_00L));
        // Цена и без фикса ниже 1 ₽ — не «дотягиваем» её вверх.
        assertEquals(50, MoneyUtil.applyPromoDiscount(50, 0, 10L));
    }

    @Test
    void distributeReduction_exactSumAndProportion() {
        long[] shares = MoneyUtil.distributeReduction(new long[]{100_00, 300_00}, 100_00);
        assertEquals(100_00, Arrays.stream(shares).sum());
        assertArrayEquals(new long[]{25_00, 75_00}, shares);
    }

    @Test
    void distributeReduction_remainderKopecksLandOnLargestRemainder() {
        // 100 коп. на три равные позиции: 33+33+34, сумма сходится копейка в копейку.
        long[] shares = MoneyUtil.distributeReduction(new long[]{10_00, 10_00, 10_00}, 100);
        assertEquals(100, Arrays.stream(shares).sum());
        for (long share : shares) {
            assertTrue(share == 33 || share == 34);
        }
    }

    @Test
    void distributeReduction_keepsKopeckPerLineWhenFeasible() {
        // Рабочий максимум скидки в проде: subtotal − MIN_PAYABLE (остаётся 1 ₽).
        long[] lines = {1_00, 50_00};
        long[] shares = MoneyUtil.distributeReduction(lines, 50_00);
        assertEquals(50_00, Arrays.stream(shares).sum());
        for (int i = 0; i < lines.length; i++) {
            assertTrue(shares[i] < lines[i]);
        }
    }

    @Test
    void distributeReduction_extremeReductionStaysWithinLines() {
        // Скидка почти во всю сумму: чью-то позицию придётся обнулить,
        // но сумма сходится и в минус никто не уходит.
        long[] lines = {1_00, 50_00};
        long[] shares = MoneyUtil.distributeReduction(lines, 50_99);
        assertEquals(50_99, Arrays.stream(shares).sum());
        for (int i = 0; i < lines.length; i++) {
            assertTrue(shares[i] <= lines[i]);
        }
    }

    @Test
    void distributeReduction_rejectsFullOrNegativeReduction() {
        assertThrows(IllegalArgumentException.class,
                () -> MoneyUtil.distributeReduction(new long[]{10_00}, 10_00));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyUtil.distributeReduction(new long[]{10_00}, -1));
    }

    @Test
    void formatRubles_groupsThousandsAndKeepsKopecks() {
        assertEquals("3 000 ₽", MoneyUtil.formatRubles(3_000_00));
        assertEquals("500 ₽", MoneyUtil.formatRubles(500_00));
        assertEquals("1 234 567 ₽", MoneyUtil.formatRubles(1_234_567_00));
        assertEquals("2 999,50 ₽", MoneyUtil.formatRubles(2_999_50));
    }
}
