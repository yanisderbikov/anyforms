package ru.anyforms.repository;

import ru.anyforms.model.Order;

import java.util.Optional;

public interface GetterOrderByTracker {
    default Order getOrderByTracker(String tracker) {
        var order = getOptionalOrderByTracker(tracker);
        if (order.isPresent()) {
            return order.get();
        }
        throw new IllegalArgumentException("Order not found with tracker :" + tracker);
    }
    Optional<Order> getOptionalOrderByTracker(String tracker);
}
