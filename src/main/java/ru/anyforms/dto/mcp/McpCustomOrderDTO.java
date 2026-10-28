package ru.anyforms.dto.mcp;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.dto.CustomProductItemDTO;
import ru.anyforms.dto.OrderSummaryDTO;

import java.util.List;

@Schema(description = "Заказ под заказ со всеми позициями, включая отправленные и завершённые")
public record McpCustomOrderDTO(OrderSummaryDTO order, List<CustomProductItemDTO> items) {
}
