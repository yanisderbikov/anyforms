package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Статистика по типу заказа: суммы и разбивка по шагам цепочки")
public record BotAnalyticsTypeDTO(
        @Schema(description = "Значение enum OrderType") String type,
        String label,
        long sent,
        long blocked,
        long failed,
        List<BotAnalyticsStepDTO> steps
) {
}
