package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

/** Оплаченные продажи одного продукта за период. */
@Schema(description = "Оплаченные продажи одного продукта за период")
public record ProductSalesDTO(

        @Schema(description = "Код продукта", example = "GUIDE")
        String code,

        @Schema(description = "Количество оплаченных покупок", example = "10")
        long quantity,

        @Schema(description = "Сумма в копейках", example = "1490000")
        long amountKopecks) {
}
