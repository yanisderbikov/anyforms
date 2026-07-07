package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.amo.AmoProductDTO;
import ru.anyforms.integration.AmoCrmGateway;

import java.util.List;

@RestController
@RequestMapping("/api/amo")
@RequiredArgsConstructor
@Tag(name = "AmoCRM", description = "Данные из AmoCRM для админки")
public class AmoProductController {

    private final AmoCrmGateway amoCrmGateway;

    @Value("${amocrm.products.catalog.id}")
    private Long productsCatalogId;

    @Operation(
            summary = "Товары каталога АМО (для выпадающего списка в админке товаров)",
            security = @SecurityRequirement(name = "Bearer")
    )
    @GetMapping("/products")
    public ResponseEntity<List<AmoProductDTO>> products() {
        List<AmoProductDTO> products = amoCrmGateway.getCatalogElements(productsCatalogId).stream()
                .map(p -> new AmoProductDTO(p.getId(), p.getName()))
                .toList();
        return ResponseEntity.ok(products);
    }
}
