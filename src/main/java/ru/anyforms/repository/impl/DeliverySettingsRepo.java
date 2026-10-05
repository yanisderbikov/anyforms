package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.marketplace.DeliverySettings;

@Repository
interface DeliverySettingsRepo extends JpaRepository<DeliverySettings, Short> {
}
