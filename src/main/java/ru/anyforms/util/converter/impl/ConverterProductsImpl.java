package ru.anyforms.util.converter.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import ru.anyforms.util.converter.ConverterProducts;

@Component
@RequiredArgsConstructor
class ConverterProductsImpl implements ConverterProducts {

    private final GetterPhotosFromS3Folder getterPhotosFromS3Folder;

    @Override
    public ProductDTO convert(Product product) {
        var photos = getterPhotosFromS3Folder.getPhotos(product.getS3PhotosFolderPath());
        return new ProductDTO(
                product.getName(),
                product.getDescription(),
                photos,
                product.getPrice(),
                product.getCrossedPrice(),
                product.getDiscountPercent(),
                product.getTgLink()
        );
    }
}
