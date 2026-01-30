package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.repository.GetterProduct;

import java.util.List;

@Component
@AllArgsConstructor
class ProductManager implements GetterProduct {

    private final ProductRepo productRepo;

    @Override
    public List<Product> getAllProducts() {
        try {
            return productRepo.findAllOrderByOrderNumberAscNullsLast();
        } catch (Exception e) {
            throw new RuntimeException("Database exception", e);
        }
    }
}
