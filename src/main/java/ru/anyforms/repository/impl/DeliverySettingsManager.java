package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.marketplace.DeliverySettings;
import ru.anyforms.repository.GetterDeliverySettings;
import ru.anyforms.repository.SaverDeliverySettings;

import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class DeliverySettingsManager implements GetterDeliverySettings, SaverDeliverySettings {

    private final DeliverySettingsRepo deliverySettingsRepo;

    @Override
    public Optional<DeliverySettings> get() {
        try {
            return deliverySettingsRepo.findById(DeliverySettings.SINGLETON_ID);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public DeliverySettings save(DeliverySettings settings) {
        try {
            return deliverySettingsRepo.save(settings);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
