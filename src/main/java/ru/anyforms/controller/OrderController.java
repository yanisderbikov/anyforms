package ru.anyforms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.anyforms.dto.ApiResponseDTO;
import ru.anyforms.dto.OrderSummaryDTO;
import ru.anyforms.dto.SetTrackerRequestDTO;
import ru.anyforms.dto.SyncOrderRequestDTO;
import ru.anyforms.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    /**
     * Получает все заказы без трекера, сгруппированные по заказчику
     * Возвращает структуру: сколько товаров для какого заказчика нужно отправить относительно сделки
     */
    @GetMapping("/without-tracker")
    public ResponseEntity<List<OrderSummaryDTO>> getOrdersWithoutTracker() {
        List<OrderSummaryDTO> summaries = orderService.getOrdersWithoutTrackerDTOs();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Устанавливает трекер для заказа
     * После установки обновляет данные в Google Sheets и AmoCRM
     */
    @PostMapping("/tracker")
    public ResponseEntity<ApiResponseDTO> setTracker(@RequestBody SetTrackerRequestDTO request) {
        ApiResponseDTO response = orderService.setTracker(request);
        HttpStatus status = response.getSuccess() == null 
                ? HttpStatus.BAD_REQUEST 
                : response.getSuccess() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Синхронизирует заказ из AmoCRM в БД
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponseDTO> syncOrder(@RequestBody SyncOrderRequestDTO request) {
        ApiResponseDTO response = orderService.syncOrder(request);
        HttpStatus status = response.getSuccess() == null 
                ? HttpStatus.BAD_REQUEST 
                : response.getSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }
}

