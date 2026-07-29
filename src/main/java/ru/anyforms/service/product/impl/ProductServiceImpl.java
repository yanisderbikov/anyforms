package ru.anyforms.service.product.impl;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.dto.marketplace.ProductVariantRequestDTO;
import ru.anyforms.model.marketplace.Product;
import ru.anyforms.model.marketplace.ProductVariant;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterProduct;
import ru.anyforms.repository.SaverProduct;
import ru.anyforms.service.product.ProductService;
import ru.anyforms.service.product.ShopService;
import ru.anyforms.service.s3.GetterPhotosFromS3Folder;
import ru.anyforms.service.s3.S3FileStorage;
import ru.anyforms.util.converter.ConverterProducts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
class ProductServiceImpl implements ProductService {

    private static final String SHOP_PREFIX = "shop/";

    private final GetterProduct getterProduct;
    private final SaverProduct saverProduct;
    private final ConverterProducts converterProducts;
    private final GetterPhotosFromS3Folder getterPhotosFromS3Folder;
    private final S3FileStorage s3FileStorage;
    private final ShopService shopService;

    @Override
    public List<ProductDTO> getAllProducts(String shopSlug) {
        var products = getterProduct.getAllProducts().stream()
                .filter(p -> shopSlug == null || shopSlug.isBlank() || isInShop(p, shopSlug.trim()))
                .toList();
        return converterProducts.convert(products);
    }

    @Override
    public List<ProductDTO> getActiveProducts(String shopSlug) {
        String slug = shopSlug == null || shopSlug.isBlank() ? Shop.DEFAULT_SLUG : shopSlug.trim();
        var products = getterProduct.getAllProducts().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActive()))
                .filter(p -> isVisibleInShop(p, slug))
                .toList();
        return converterProducts.convert(products);
    }

    private boolean isInShop(Product product, String slug) {
        return product.getShops() != null
                && product.getShops().stream().anyMatch(s -> s.getSlug().equals(slug));
    }

    private boolean isVisibleInShop(Product product, String slug) {
        return product.getShops() != null
                && product.getShops().stream()
                .anyMatch(s -> s.getSlug().equals(slug) && !Boolean.FALSE.equals(s.getActive()));
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
    public ProductDTO deletePhoto(UUID id, String fileName) {
        Product product = getterProduct.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден: " + id));
        String folder = product.getS3PhotosFolderPath();
        if (folder == null || folder.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У товара нет папки с фото");
        }
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректное имя файла: " + fileName);
        }
        s3FileStorage.delete(SHOP_PREFIX + folder + "/" + fileName);
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
        Set<Shop> shops = request.getShopSlugs() == null
                ? Set.of(shopService.resolveBySlug(null))
                : resolveShops(request.getShopSlugs());
        Product product = Product.builder()
                .shops(shops)
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
        if (request.getVariants() != null) {
            applyVariants(product, request.getVariants());
        }
        return product;
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
        if (request.getShopSlugs() != null) {
            product.setShops(resolveShops(request.getShopSlugs()));
        }
        if (request.getVariants() != null) {
            applyVariants(product, request.getVariants());
        }
        return product;
    }

    private Set<Shop> resolveShops(List<String> slugs) {
        return slugs.stream()
                .filter(slug -> slug != null && !slug.isBlank())
                .map(shopService::resolveBySlug)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void applyVariants(Product product, List<ProductVariantRequestDTO> requested) {
        Map<UUID, ProductVariant> existing = product.getVariants().stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        List<ProductVariant> result = new ArrayList<>();
        int position = 0;
        for (ProductVariantRequestDTO dto : requested) {
            if (dto.getLabel() == null || dto.getLabel().isBlank()
                    || dto.getPrice() == null || dto.getPrice().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "У варианта товара должны быть заполнены название и цена");
            }
            ProductVariant variant = dto.getId() == null ? null : existing.get(dto.getId());
            if (variant == null) {
                variant = new ProductVariant();
                variant.setProduct(product);
            }
            variant.setLabel(dto.getLabel().trim());
            variant.setPrice(dto.getPrice().trim());
            variant.setOrderNumber(position++);
            result.add(variant);
        }
        product.getVariants().clear();
        product.getVariants().addAll(result);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
