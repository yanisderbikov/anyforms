package ru.anyforms.repository;

import ru.anyforms.model.marketplace.DeliverySettings;

import java.util.Optional;

public interface GetterDeliverySettings {
    Optional<DeliverySettings> get();
}
