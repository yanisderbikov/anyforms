package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.SaverProduct;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class ProductManager implements GetterProduct, SaverProduct {

    private final ProductRepo productRepo;

    @Override
    public List<Product> getAllProducts() {
        try {
            return productRepo.findAllOrderByOrderNumberAscNullsLast();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Product> getById(UUID id) {
        try {
            return productRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Product save(Product product) {
        try {
            return productRepo.save(product);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
