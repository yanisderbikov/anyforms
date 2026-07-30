package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.anyforms.dto.marketplace.PhotoOrderRequestDTO;
import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;

import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.service.product.ProductService;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@RestController
@RequestMapping("/api/product")
@Tag(name = "Product", description = "API для управления продуктами")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Получить продукты витрины",
            description = "Только активные товары, продающиеся в магазине. Без параметра shop — витрина по умолчанию (anyforms), "
                    + "с shop=<slug> — витрина этого магазина")
    @GetMapping()
    public ResponseEntity<List<ProductDTO>> getAllProducts(
            @RequestParam(value = "shop", required = false) String shopSlug) {
        var products = productService.getActiveProducts(shopSlug);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Получить все продукты для админки",
            description = "Все товары всех магазинов, включая выключенные из продажи. shop=<slug> — только товары, продающиеся в этом магазине",
            security = @SecurityRequirement(name = "Bearer")
    )
    @GetMapping("/all")
    public ResponseEntity<List<ProductDTO>> getAllProductsAdmin(
            @RequestParam(value = "shop", required = false) String shopSlug) {
        var products = productService.getAllProducts(shopSlug);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Загрузить фото товара",
            description = "Multipart-загрузка изображений в S3-папку товара; если папка не задана, создаётся по id товара",
            security = @SecurityRequirement(name = "Bearer")
    )
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDTO> uploadPhotos(
            @PathVariable("id") UUID id,
            @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.ok(productService.uploadPhotos(id, files));
    }

    @Operation(summary = "Удалить фото товара",
            description = "Удаляет файл по имени из S3-папки товара",
            security = @SecurityRequirement(name = "Bearer")
    )
    @DeleteMapping("/{id}/photos")
    public ResponseEntity<ProductDTO> deletePhoto(
            @PathVariable("id") UUID id,
            @RequestParam("file") String fileName) {
        return ResponseEntity.ok(productService.deletePhoto(id, fileName));
    }

    @Operation(summary = "Изменить порядок фото товара",
            description = "Имена файлов из папки товара в нужном порядке; первое имя — главное фото на витрине",
            security = @SecurityRequirement(name = "Bearer")
    )
    @PutMapping("/{id}/photos/order")
    public ResponseEntity<ProductDTO> reorderPhotos(
            @PathVariable("id") UUID id,
            @Valid @RequestBody PhotoOrderRequestDTO request) {
        return ResponseEntity.ok(productService.reorderPhotos(id, request.getFileNames()));
    }

    @Operation(summary = "Получить товар по id",
            description = "Для страницы редактирования товара в админке",
            security = @SecurityRequirement(name = "Bearer")
    )
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @Operation(summary = "Создать или обновить продукт",
            description = "POST с телом продукта. Если передан id — обновляется продукт с этим id. Иначе создаётся новый. Поле folder — папка в S3 (под shop/) с фото.",
            security = @SecurityRequirement(name = "Bearer")
    )
    @PostMapping("/create")
    public ResponseEntity<ProductDTO> saveOrUpdateProduct(@RequestBody ProductCreateUpdateRequestDTO request) {
        ProductDTO result = productService.saveOrUpdate(request);
        return ResponseEntity.ok(result);
    }
}
