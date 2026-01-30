package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.marketplace.ProductDTO;
import ru.anyforms.service.product.ProductService;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/product")
@Tag(name = "Product", description = "API для управления продуктами")
public class ProductController {

    private final ProductService productService;

    @Operation(
            summary = "Получить все продукты"
    )
    @GetMapping()
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        var products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }
}
