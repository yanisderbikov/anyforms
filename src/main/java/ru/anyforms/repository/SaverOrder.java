package ru.anyforms.repository;

import ru.anyforms.model.Order;

import java.util.Optional;

public interface SaverOrder {
    void save(Order order);
}
