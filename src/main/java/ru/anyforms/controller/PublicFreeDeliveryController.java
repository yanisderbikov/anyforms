package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.delivery.FreeDeliveryDTO;
import ru.anyforms.service.delivery.FreeDeliveryService;

@RestController
@RequestMapping("/api/public/free-delivery")
@RequiredArgsConstructor
@Tag(name = "PublicFreeDelivery", description = "Условие бесплатной доставки для корзины и чекаута")
public class PublicFreeDeliveryController {

    private final FreeDeliveryService freeDeliveryService;

    @Operation(summary = "Условие бесплатной доставки",
            description = "Доставка бесплатная, если акция включена и сумма к оплате после промокода не меньше порога")
    @GetMapping
    public ResponseEntity<FreeDeliveryDTO> get() {
        return ResponseEntity.ok(freeDeliveryService.get());
    }
}
