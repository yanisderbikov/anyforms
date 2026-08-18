package ru.anyforms.service.product;

import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.service.s3.S3FileStorage;

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

    /**
     * Presigned PUT для загрузки фото напрямую в S3-папку товара, минуя бэкенд;
     * если папка не задана, создаётся по id товара.
     */
    S3FileStorage.PresignedUpload presignPhotoUpload(UUID id, String filename, String contentType);

    /** Фиксирует загруженные напрямую фото: сбрасывает кеш папки и возвращает товар со свежим списком. */
    ProductDTO confirmPhotos(UUID id);

    ProductDTO deletePhoto(UUID id, String fileName);

    /** Порядок фото товара на витрине: имена файлов из папки товара в нужном порядке. */
    ProductDTO reorderPhotos(UUID id, List<String> fileNames);

    ProductDTO getById(UUID id);
}
