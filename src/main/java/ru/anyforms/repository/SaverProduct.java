package ru.anyforms.repository;

import ru.anyforms.model.marketplace.Product;

public interface SaverProduct {
    Product save(Product product);
}
