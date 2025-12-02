package ru.anyforms.service;

public interface DeliveryProcessor {
    void updateStatus(String trackerNumber, String statusText);
    void updateStatus(int rowNumber, String statusText);
    void updateAmoCrmStatusIfNeeded(Long leadId, String trackingNumber, String statusCode);

    void processAcceptedForDelivery(String trackerNumber, Long leadId);
    void processHandedTo(String trackerNumber, Long leadId);
    void processDelivered(String trackerNumber, Long leadId);
}
