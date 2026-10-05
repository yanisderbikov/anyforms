package ru.anyforms.service.delivery.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.delivery.FreeDeliveryDTO;
import ru.anyforms.dto.delivery.FreeDeliveryUpdateRequest;
import ru.anyforms.model.marketplace.DeliverySettings;
import ru.anyforms.repository.GetterDeliverySettings;
import ru.anyforms.repository.SaverDeliverySettings;
import ru.anyforms.service.delivery.FreeDeliveryService;

@Service
@RequiredArgsConstructor
@Slf4j
class FreeDeliveryServiceImpl implements FreeDeliveryService {

    static final long DEFAULT_THRESHOLD_KOPECKS = 1_200_000L;

    private final GetterDeliverySettings getterDeliverySettings;
    private final SaverDeliverySettings saverDeliverySettings;

    @Override
    public FreeDeliveryDTO get() {
        return FreeDeliveryDTO.from(current());
    }

    @Override
    public FreeDeliveryDTO update(FreeDeliveryUpdateRequest request) {
        DeliverySettings settings = current();
        settings.setFreeDeliveryEnabled(request.getEnabled());
        settings.setFreeDeliveryThresholdKopecks(request.getThresholdKopecks());
        DeliverySettings saved = saverDeliverySettings.save(settings);
        log.info("Бесплатная доставка: {} от {} коп.",
                Boolean.TRUE.equals(saved.getFreeDeliveryEnabled()) ? "включена" : "выключена",
                saved.getFreeDeliveryThresholdKopecks());
        return FreeDeliveryDTO.from(saved);
    }

    @Override
    public boolean qualifies(long payableKopecks) {
        return current().qualifiesForFreeDelivery(payableKopecks);
    }

    private DeliverySettings current() {
        return getterDeliverySettings.get().orElseGet(() -> DeliverySettings.builder()
                .id(DeliverySettings.SINGLETON_ID)
                .freeDeliveryEnabled(false)
                .freeDeliveryThresholdKopecks(DEFAULT_THRESHOLD_KOPECKS)
                .build());
    }
}
