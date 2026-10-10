package ru.anyforms.repository;

import ru.anyforms.model.marketplace.DeliverySettings;

public interface SaverDeliverySettings {
    DeliverySettings save(DeliverySettings settings);
}
