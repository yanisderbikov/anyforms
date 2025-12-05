package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Запрос на установку трекера для заказа")
public class SetTrackerAndCommentRequestDTO {
    @Schema(description = "ID сделки в AmoCRM", example = "12345", required = true)
    private Long leadId;
    
    @Schema(description = "Номер трекера", example = "CDEK123456789", required = true)
    private String tracker;

    @Schema(description = "Комментарий", example = "коммент", required = true)
    private String comment;
}

