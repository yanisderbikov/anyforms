package ru.anyforms.service;

import ru.anyforms.model.Order;

public interface DeliveryNotifier {

    void notifyShipped(Order order, String tracker);

    void notifyArrivedAtPvz(Order order);

    void notifyReadyForPickup(Order order);
}
