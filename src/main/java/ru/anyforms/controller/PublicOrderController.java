package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.PublicOrderDTO;
import ru.anyforms.service.PublicOrderService;

@RestController
@RequestMapping("/api/public/orders")
@RequiredArgsConstructor
@Tag(name = "PublicOrder", description = "Публичная информация о заказе маркетплейса по его номеру")
public class PublicOrderController {

    private final PublicOrderService publicOrderService;

    @Operation(summary = "Заказ по публичному номеру",
            description = "Состав заказа, суммы и ПВЗ для страницы после оплаты; без персональных данных")
    @GetMapping("/{publicId}")
    public PublicOrderDTO get(@PathVariable String publicId) {
        return publicOrderService.getByPublicId(publicId);
    }
}
