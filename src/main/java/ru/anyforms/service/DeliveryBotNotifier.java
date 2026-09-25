package ru.anyforms.service;

import ru.anyforms.dto.cdek.CdekDeliveryEta;

public interface DeliveryBotNotifier {

    void notifyShipped(Long leadId, String tracker, CdekDeliveryEta eta);

    void notifyCdekReadyToPickup(Long leadId);

    void notifyPickupReady(Long leadId);
}
