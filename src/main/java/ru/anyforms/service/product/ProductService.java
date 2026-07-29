package ru.anyforms.service.product;

import org.springframework.web.multipart.MultipartFile;
import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ProductDTO;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    /** Все товары для админки. shopSlug null/пустой — товары всех магазинов. */
    List<ProductDTO> getAllProducts(String shopSlug);

    /**
     * Товары витрины. shopSlug null/пустой — общая витрина /shop: активные товары
     * всех активных магазинов. Иначе — только товары указанного магазина.
     */
    List<ProductDTO> getActiveProducts(String shopSlug);

    ProductDTO saveOrUpdate(ProductCreateUpdateRequestDTO request);

    ProductDTO uploadPhotos(UUID id, List<MultipartFile> files);

    ProductDTO deletePhoto(UUID id, String fileName);
}
