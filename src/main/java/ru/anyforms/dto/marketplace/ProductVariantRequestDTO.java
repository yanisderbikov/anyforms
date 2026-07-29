package ru.anyforms.dto.marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Вариант товара в запросе создания/обновления. Порядок в списке — порядок показа на витрине.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ProductVariantRequestDTO {
    /**
     * ID существующего варианта — обновить его (сохраняет вариант в корзинах покупателей).
     * Без id создаётся новый вариант.
     */
    private UUID id;
    /** Название варианта: «80 мл», «20 см». */
    private String label;
    /** Цена варианта в рублях, строкой, как у товара. */
    private String price;
}
