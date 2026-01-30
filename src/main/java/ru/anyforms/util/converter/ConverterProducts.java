package ru.anyforms.util.converter;

import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.model.marketplace.Product;

import java.util.ArrayList;
import java.util.List;

public interface ConverterProducts {
    ProductDTO convert(Product product);
    default List<ProductDTO> convert(List<Product> products) {
        List<ProductDTO> productDTOS = new ArrayList<>();
        for (var p : products) {
            productDTOS.add(convert(p));
        }
        return productDTOS;
    }
}
