package ru.anyforms.repository;

import ru.anyforms.model.Order;

import java.util.List;

public interface GetterOrder {
    List<Order> getEmptyDeliveryAndNonEmptyTracker();
    List<Order> getNonDeliveredOrders();
}
