package ru.anyforms.service.product;

import ru.anyforms.dto.marketplace.ProductDTO;

import java.util.List;

public interface ProductService {
    List<ProductDTO> getAllProducts();
}
