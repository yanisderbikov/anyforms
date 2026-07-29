package ru.anyforms.util.converter.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.dto.marketplace.ProductVariantDTO;
import ru.anyforms.dto.marketplace.ShopDTO;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.model.marketplace.ProductVariant;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import ru.anyforms.util.converter.ConverterProducts;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
                product.getPreorder(),
                convertShops(product.getShops()),
                convertVariants(product.getVariants())
        );
    }

    private List<ProductVariantDTO> convertVariants(Set<ProductVariant> variants) {
        if (variants == null) {
            return List.of();
        }
        return variants.stream()
                .sorted(Comparator.comparing(v -> v.getOrderNumber() == null ? 0 : v.getOrderNumber()))
                .map(v -> new ProductVariantDTO(v.getId(), v.getLabel(), v.getPrice()))
                .toList();
    }

    private List<ShopDTO> convertShops(Set<Shop> shops) {
        if (shops == null) {
            return List.of();
        }
        return shops.stream()
                .sorted(Comparator.comparing(Shop::getName))
                .map(shop -> new ShopDTO(shop.getId(), shop.getSlug(), shop.getName(), shop.getActive()))
                .toList();
    }
}
