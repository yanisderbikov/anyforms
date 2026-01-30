package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.marketplace.ProductCreateUpdateRequestDTO;

import jakarta.validation.Valid;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.service.product.ProductService;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/product")
@Tag(name = "Product", description = "API для управления продуктами")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Получить все продукты")
    @GetMapping()
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        var products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Создать или обновить продукт",
            description = "POST с телом продукта. Если передан id — обновляется продукт с этим id. Иначе создаётся новый. Поле folder — папка в S3 (под shop/) с фото.")
    @PostMapping()
    public ResponseEntity<ProductDTO> saveOrUpdateProduct(@RequestBody ProductCreateUpdateRequestDTO request) {
        ProductDTO result = productService.saveOrUpdate(request);
        return ResponseEntity.ok(result);
    }
}
