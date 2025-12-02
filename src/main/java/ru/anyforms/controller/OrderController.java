package ru.anyforms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "API для управления заказами")
public class OrderController {
    private final OrderService orderService;

    /**
     * Получает все заказы без трекера, сгруппированные по заказчику
     * Возвращает структуру: сколько товаров для какого заказчика нужно отправить относительно сделки
     */
    @Operation(
            summary = "Получить заказы без трекера",
            description = "Возвращает список всех заказов без трекера, сгруппированных по заказчику"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешное получение списка заказов",
                    content = @Content(schema = @Schema(implementation = OrderSummaryDTO.class))
            )
    })
    @GetMapping("/without-tracker")
    public ResponseEntity<List<OrderSummaryDTO>> getOrdersWithoutTracker() {
        List<OrderSummaryDTO> summaries = orderService.getOrdersWithoutTrackerDTOs();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Устанавливает трекер для заказа
     * После установки обновляет данные в Google Sheets и AmoCRM
     */
    @Operation(
            summary = "Установить трекер для заказа",
            description = "Устанавливает трекер для заказа и обновляет данные в Google Sheets и AmoCRM"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Трекер успешно установлен",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Неверный запрос",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Внутренняя ошибка сервера",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
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
    @Operation(
            summary = "Синхронизировать заказ из AmoCRM",
            description = "Синхронизирует заказ из AmoCRM в базу данных"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Заказ успешно синхронизирован",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Неверный запрос",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Заказ не найден",
                    content = @Content(schema = @Schema(implementation = ApiResponseDTO.class))
            )
    })
    @PostMapping("/sync")
    public ResponseEntity<ApiResponseDTO> syncOrder(@RequestBody SyncOrderRequestDTO request) {
        ApiResponseDTO response = orderService.syncOrder(request);
        HttpStatus status = response.getSuccess() == null 
                ? HttpStatus.BAD_REQUEST 
                : response.getSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/sync/list")
    public ResponseEntity<Void> syncOrder(@RequestBody List<SyncOrderRequestDTO> list) {
        for (var request : list) {
            orderService.syncOrder(request);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
}

