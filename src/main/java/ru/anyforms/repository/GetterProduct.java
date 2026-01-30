package ru.anyforms.repository;

import ru.anyforms.model.marketplace.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterProduct {
    List<Product> getAllProducts();

    Optional<Product> getById(UUID id);
}
