package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.anyforms.dto.cdek.CdekPvzDTO;
import ru.anyforms.dto.cdek.DeliveryCostRequestDTO;
import ru.anyforms.dto.cdek.DeliveryCostResponseDTO;
import ru.anyforms.exception.CdekCalculationException;
import ru.anyforms.service.impl.CdekDeliveryCostService;
import ru.anyforms.service.impl.CdekPvzService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cdek")
@RequiredArgsConstructor
@Tag(name = "CDEK", description = "Интеграция со СДЭК: пункты выдачи заказов и расчёт доставки")
public class CdekPvzController {

    private final CdekPvzService cdekPvzService;
    private final CdekDeliveryCostService deliveryCostService;

    @Operation(summary = "Саджест ПВЗ СДЭК: текстовый поиск по улице/городу/региону по всем странам присутствия СДЭК (для дропдауна на чекауте)")
    @GetMapping("/pvz")
    public ResponseEntity<List<CdekPvzDTO>> pvz(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(cdekPvzService.search(query));
    }

    @Operation(summary = "Расчёт стоимости и срока доставки СДЭК до выбранного ПВЗ по позициям корзины (для чекаута)")
    @PostMapping("/delivery-cost")
    public ResponseEntity<DeliveryCostResponseDTO> deliveryCost(@Valid @RequestBody DeliveryCostRequestDTO request) {
        return ResponseEntity.ok(deliveryCostService.calculate(request.getItems(), request.getPvzCode()));
    }

    @ExceptionHandler(CdekCalculationException.class)
    public ResponseEntity<Map<String, String>> handleCalculationError(CdekCalculationException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
