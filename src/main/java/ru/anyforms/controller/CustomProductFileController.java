package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.service.CustomProductItemService;

@RestController
@RequestMapping("/api/custom-product-files")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "CustomProductFiles", description = "Файлы кастомных позиций (поштучное удаление)")
public class CustomProductFileController {

    private final CustomProductItemService service;

    @Operation(summary = "Удалить один файл по его id")
    @DeleteMapping("/{fileId}")
    public CustomProductItemDTO delete(@PathVariable Long fileId) {
        return service.removeFile(fileId);
    }
}
