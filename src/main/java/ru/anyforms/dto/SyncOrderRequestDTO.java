package ru.anyforms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Запрос на синхронизацию заказа из AmoCRM")
public class SyncOrderRequestDTO {
    @Schema(description = "ID сделки в AmoCRM", example = "12345", required = true)
    private Long leadId;
}

