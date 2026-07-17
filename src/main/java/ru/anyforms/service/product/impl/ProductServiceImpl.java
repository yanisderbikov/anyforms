package ru.anyforms.service.product.impl;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.SaverProduct;
import ru.anyforms.service.product.ProductService;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import ru.anyforms.service.s3.S3FileStorage;
import ru.anyforms.util.converter.ConverterProducts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
class ProductServiceImpl implements ProductService {

    private static final String SHOP_PREFIX = "shop/";

    private final GetterProduct getterProduct;
    private final SaverProduct saverProduct;
    private final ConverterProducts converterProducts;
    private final GetterPhotosFromS3Folder getterPhotosFromS3Folder;
    private final S3FileStorage s3FileStorage;

    @Override
    public List<ProductDTO> getAllProducts() {
        var products = getterProduct.getAllProducts();
        return converterProducts.convert(products);
    }

    @Override
    public List<ProductDTO> getActiveProducts() {
        var products = getterProduct.getAllProducts().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActive()))
                .toList();
        return converterProducts.convert(products);
    }

    @Override
    public ProductDTO uploadPhotos(UUID id, List<MultipartFile> files) {
        Product product = getterProduct.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден: " + id));
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файлы не переданы");
        }
        if (product.getS3PhotosFolderPath() == null || product.getS3PhotosFolderPath().isBlank()) {
            product.setS3PhotosFolderPath(product.getId().toString());
            product = saverProduct.save(product);
        }
        String folder = product.getS3PhotosFolderPath();
        for (MultipartFile file : files) {
            s3FileStorage.upload(file, SHOP_PREFIX + folder);
        }
        getterPhotosFromS3Folder.invalidateFolder(folder);
        return converterProducts.convert(product);
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
        if (request.getFolder() != null && !request.getFolder().isBlank()) {
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
                .s3PhotosFolderPath(blankToNull(request.getFolder()))
                .price(request.getPrice())
                .crossedPrice(request.getCrossedPrice())
                .discountPercent(request.getDiscountPercent())
                .tgLink(blankToNull(request.getTgLink()))
                .orderNumber(request.getOrderNumber())
                .amoProductId(request.getAmoProductId())
                .amoProductName(request.getAmoProductName())
                .active(request.getActive() == null || request.getActive())
                .preorder(Boolean.TRUE.equals(request.getPreorder()))
                .build();
    }

    private Product mapRequestOntoProduct(ProductCreateUpdateRequestDTO request, Product product) {
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getFolder() != null && !request.getFolder().isBlank()) {
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
            product.setTgLink(blankToNull(request.getTgLink()));
        }
        if (request.getOrderNumber() != null) {
            product.setOrderNumber(request.getOrderNumber());
        }
        if (request.getAmoProductId() != null) {
            product.setAmoProductId(request.getAmoProductId());
        }
        if (request.getAmoProductName() != null) {
            product.setAmoProductName(request.getAmoProductName());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        if (request.getPreorder() != null) {
            product.setPreorder(request.getPreorder());
        }
        return product;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
