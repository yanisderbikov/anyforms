package ru.anyforms.service.delivery.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.dto.delivery.FreeDeliveryDTO;
import ru.anyforms.dto.delivery.FreeDeliveryUpdateRequest;
import ru.anyforms.model.marketplace.DeliverySettings;
import ru.anyforms.repository.GetterDeliverySettings;
import ru.anyforms.repository.SaverDeliverySettings;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreeDeliveryServiceImplTest {

    private final GetterDeliverySettings getter = mock(GetterDeliverySettings.class);
    private final SaverDeliverySettings saver = mock(SaverDeliverySettings.class);
    private final FreeDeliveryServiceImpl service = new FreeDeliveryServiceImpl(getter, saver);

    @Test
    void qualifiesUsesStoredThreshold() {
        when(getter.get()).thenReturn(Optional.of(settings(true, 1_200_000L)));

        assertTrue(service.qualifies(1_200_000L));
        assertFalse(service.qualifies(1_199_999L));
    }

    @Test
    void missingRowMeansDisabled() {
        when(getter.get()).thenReturn(Optional.empty());

        FreeDeliveryDTO dto = service.get();

        assertFalse(dto.enabled());
        assertEquals(FreeDeliveryServiceImpl.DEFAULT_THRESHOLD_KOPECKS, dto.thresholdKopecks());
        assertFalse(service.qualifies(10_000_000L));
    }

    @Test
    void updateSavesEnabledAndThreshold() {
        when(getter.get()).thenReturn(Optional.of(settings(true, 1_200_000L)));
        when(saver.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FreeDeliveryDTO dto = service.update(new FreeDeliveryUpdateRequest(false, 1_500_000L));

        ArgumentCaptor<DeliverySettings> saved = ArgumentCaptor.forClass(DeliverySettings.class);
        verify(saver).save(saved.capture());
        assertEquals(DeliverySettings.SINGLETON_ID, saved.getValue().getId());
        assertFalse(saved.getValue().getFreeDeliveryEnabled());
        assertEquals(1_500_000L, saved.getValue().getFreeDeliveryThresholdKopecks());
        assertFalse(dto.enabled());
        assertEquals(1_500_000L, dto.thresholdKopecks());
    }

    private static DeliverySettings settings(boolean enabled, long thresholdKopecks) {
        return DeliverySettings.builder()
                .id(DeliverySettings.SINGLETON_ID)
                .freeDeliveryEnabled(enabled)
                .freeDeliveryThresholdKopecks(thresholdKopecks)
                .build();
    }
}
