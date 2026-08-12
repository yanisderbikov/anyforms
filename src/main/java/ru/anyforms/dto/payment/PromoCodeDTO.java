package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PromoCode;

import java.util.UUID;

@Schema(description = "Промокод (админка)")
public record PromoCodeDTO(
        UUID id,
        @Schema(description = "Код в верхнем регистре") String code,
        @Schema(description = "Скидка в процентах, 0–100") Integer discountPercent,
        @Schema(description = "Фиксированная скидка в копейках; null — нет") Long discountAmountKopecks,
        @Schema(description = "Минимальная сумма заказа в копейках; null — без порога") Long minOrderKopecks,
        Boolean active,
        @Schema(description = "Начало действия, ISO-8601; null — без нижней границы") String validFrom,
        @Schema(description = "Окончание действия (исключительно), ISO-8601; null — бессрочно") String validUntil,
        String createdAt
) {
    /** Instant отдаём строками: без явной настройки Jackson сериализует их в epoch-секунды. */
    public static PromoCodeDTO from(PromoCode p) {
        return new PromoCodeDTO(
                p.getId(),
                p.getCode(),
                p.getDiscountPercent(),
                p.getDiscountAmountKopecks(),
                p.getMinOrderKopecks(),
                p.getActive(),
                p.getValidFrom() != null ? p.getValidFrom().toString() : null,
                p.getValidUntil() != null ? p.getValidUntil().toString() : null,
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
    }
}
