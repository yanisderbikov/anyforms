package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Стандартный ответ API")
public class ApiResponseDTO {
    @Schema(description = "Флаг успешности операции", example = "true")
    private Boolean success;
    
    @Schema(description = "Сообщение об ошибке (если есть)", example = "Заказ не найден")
    private String error;
    
    @Schema(description = "ID сделки в AmoCRM", example = "12345")
    private Long leadId;
    
    @Schema(description = "Номер трекера", example = "CDEK123456789")
    private String tracker;
    
    @Schema(description = "Количество товаров", example = "5")
    private Integer itemsCount;
}

