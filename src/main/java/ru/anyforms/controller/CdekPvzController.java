package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.cdek.CdekPvzDTO;
import ru.anyforms.service.impl.CdekPvzService;

import java.util.List;

@RestController
@RequestMapping("/api/cdek")
@RequiredArgsConstructor
@Tag(name = "CDEK", description = "Интеграция со СДЭК: пункты выдачи заказов")
public class CdekPvzController {

    private final CdekPvzService cdekPvzService;

    @Operation(summary = "Саджест ПВЗ СДЭК: текстовый поиск по улице/городу по всей России (для дропдауна на чекауте)")
    @GetMapping("/pvz")
    public ResponseEntity<List<CdekPvzDTO>> pvz(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(cdekPvzService.search(query));
    }
}
