package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.service.CustomProductItemService;

/**
 * Публичный (без токена) просмотр одной позиции — для шаринга ссылкой.
 * Только чтение; правки остаются под ROLE_ADMIN в {@link CustomProductItemController}.
 */
@RestController
@RequestMapping("/api/public/custom-product-items")
@RequiredArgsConstructor
@Tag(name = "PublicCustomProductItems", description = "Публичный просмотр позиции по ссылке")
public class PublicCustomProductItemController {

    private final CustomProductItemService service;

    @Operation(summary = "Позиция по id (публично, read-only)")
    @GetMapping("/{id}")
    public CustomProductItemDTO get(@PathVariable Long id) {
        return service.getById(id);
    }
}
