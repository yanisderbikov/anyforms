package ru.anyforms.service;

public interface DeliveryProcessor {
    void updateStatus(String trackerNumber, String statusText);
    default void updateStatus(String trackerNumber) {
        updateStatus(trackerNumber, null);
    }
    @Deprecated
    void updateStatus(int rowNumber, String statusText);
    @Deprecated
    void updateAmoCrmStatusIfNeeded(Long leadId, String trackingNumber, String statusCode);
    @Deprecated
    void processAcceptedForDelivery(String trackerNumber, Long leadId);
}
