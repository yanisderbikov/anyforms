package ru.anyforms.dto.delivery;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.marketplace.DeliverySettings;

import java.time.Instant;

@Schema(description = "Бесплатная доставка: включена ли и от какой суммы заказа")
public record FreeDeliveryDTO(
        @Schema(description = "Акция включена") boolean enabled,
        @Schema(description = "Порог суммы к оплате (после промокода), копейки", example = "1200000") long thresholdKopecks,
        @Schema(description = "Когда настройку меняли последний раз") Instant updatedAt) {

    public static FreeDeliveryDTO from(DeliverySettings settings) {
        return new FreeDeliveryDTO(
                Boolean.TRUE.equals(settings.getFreeDeliveryEnabled()),
                settings.getFreeDeliveryThresholdKopecks(),
                settings.getUpdatedAt());
    }
}
