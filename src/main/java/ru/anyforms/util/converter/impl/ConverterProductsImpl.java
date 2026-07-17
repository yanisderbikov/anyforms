package ru.anyforms.util.converter.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import ru.anyforms.util.converter.ConverterProducts;

import java.util.List;

@Component
@RequiredArgsConstructor
class ConverterProductsImpl implements ConverterProducts {

    private final GetterPhotosFromS3Folder getterPhotosFromS3Folder;

    @Override
    public ProductDTO convert(Product product) {
        String folder = product.getS3PhotosFolderPath();
        var photos = folder == null || folder.isBlank()
                ? List.<String>of()
                : getterPhotosFromS3Folder.getPhotos(folder);
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                photos,
                product.getPrice(),
                product.getCrossedPrice(),
                product.getDiscountPercent(),
                product.getTgLink(),
                product.getAmoProductId(),
                product.getAmoProductName(),
                product.getActive(),
                product.getPreorder()
        );
    }
}
