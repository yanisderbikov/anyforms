package ru.anyforms.service.product.impl;


import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.service.product.ProductService;
import ru.anyforms.util.converter.ConverterProducts;

import java.util.List;

@Service
@AllArgsConstructor
class ProductServiceImpl implements ProductService {

    private final GetterProduct getterProduct;
    private final ConverterProducts converterProducts;

    @Override
    public List<ProductDTO> getAllProducts() {
        var products = getterProduct.getAllProducts();
        return converterProducts.convert(products);
    }
}
