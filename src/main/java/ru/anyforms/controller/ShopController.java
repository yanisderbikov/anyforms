package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.marketplace.ShopDTO;
import ru.anyforms.service.product.ShopService;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/shop")
@Tag(name = "Shop", description = "Магазины витрины")
public class ShopController {

    private final ShopService shopService;

    @Operation(summary = "Активные магазины",
            description = "Список магазинов: anyforms и партнёрские (у каждого своя витрина /shop/{slug})")
    @GetMapping
    public ResponseEntity<List<ShopDTO>> getShops() {
        return ResponseEntity.ok(shopService.getActiveShops());
    }
}
