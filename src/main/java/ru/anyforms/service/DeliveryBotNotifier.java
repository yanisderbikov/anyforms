package ru.anyforms.service;

public interface DeliveryBotNotifier {

    void notifyShipped(Long leadId, String tracker);

    void notifyCdekReadyToPickup(Long leadId);

    void notifyPickupReady(Long leadId);
}
