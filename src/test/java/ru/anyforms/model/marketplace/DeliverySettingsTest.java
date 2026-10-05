package ru.anyforms.model.marketplace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliverySettingsTest {

    @Test
    void freeFromThresholdInclusive() {
        DeliverySettings settings = settings(true, 1_200_000L);

        assertTrue(settings.qualifiesForFreeDelivery(1_200_000L));
        assertTrue(settings.qualifiesForFreeDelivery(1_500_000L));
        assertFalse(settings.qualifiesForFreeDelivery(1_199_999L));
    }

    @Test
    void disabledNeverFree() {
        assertFalse(settings(false, 1_200_000L).qualifiesForFreeDelivery(5_000_000L));
    }

    private static DeliverySettings settings(boolean enabled, long thresholdKopecks) {
        return DeliverySettings.builder()
                .id(DeliverySettings.SINGLETON_ID)
                .freeDeliveryEnabled(enabled)
                .freeDeliveryThresholdKopecks(thresholdKopecks)
                .build();
    }
}
