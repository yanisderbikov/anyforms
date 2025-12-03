package ru.anyforms.repository;

import ru.anyforms.model.Order;

import java.util.List;
import java.util.Optional;

public interface GetterOrder {
    List<Order> getEmptyDeliveryAndNonEmptyTracker();
}
