package ru.anyforms.service.product.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.SaverProduct;
import ru.anyforms.service.product.ProductService;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import ru.anyforms.util.converter.ConverterProducts;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
class ProductServiceImpl implements ProductService {

    private final GetterProduct getterProduct;
    private final SaverProduct saverProduct;
    private final ConverterProducts converterProducts;
    private final GetterPhotosFromS3Folder getterPhotosFromS3Folder;

    @Override
    public List<ProductDTO> getAllProducts() {
        var products = getterProduct.getAllProducts();
        return converterProducts.convert(products);
    }

    @Override
    public ProductDTO saveOrUpdate(ProductCreateUpdateRequestDTO request) {
        String oldFolder = null;
        Product product;
        if (request.getId() != null) {
            Optional<Product> existing = getterProduct.getById(request.getId());
            if (existing.isPresent()) {
                oldFolder = existing.get().getS3PhotosFolderPath();
                product = mapRequestOntoProduct(request, existing.get());
            } else {
                product = newProductFromRequest(request);
            }
        } else {
            product = newProductFromRequest(request);
        }
        Product saved = saverProduct.save(product);
        if (request.getFolder() != null) {
            getterPhotosFromS3Folder.invalidateFolder(saved.getS3PhotosFolderPath());
            if (oldFolder != null && !oldFolder.equals(saved.getS3PhotosFolderPath())) {
                getterPhotosFromS3Folder.invalidateFolder(oldFolder);
            }
        }
        return converterProducts.convert(saved);
    }

    private Product newProductFromRequest(ProductCreateUpdateRequestDTO request) {
        return Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .s3PhotosFolderPath(request.getFolder())
                .price(request.getPrice())
                .crossedPrice(request.getCrossedPrice())
                .discountPercent(request.getDiscountPercent())
                .tgLink(request.getTgLink())
                .orderNumber(request.getOrderNumber())
                .build();
    }

    private Product mapRequestOntoProduct(ProductCreateUpdateRequestDTO request, Product product) {
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getFolder() != null) {
            product.setS3PhotosFolderPath(request.getFolder());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCrossedPrice() != null) {
            product.setCrossedPrice(request.getCrossedPrice());
        }
        if (request.getDiscountPercent() != null) {
            product.setDiscountPercent(request.getDiscountPercent());
        }
        if (request.getTgLink() != null) {
            product.setTgLink(request.getTgLink());
        }
        if (request.getOrderNumber() != null) {
            product.setOrderNumber(request.getOrderNumber());
        }
        return product;
    }
}
