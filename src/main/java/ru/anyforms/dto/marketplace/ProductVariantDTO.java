package ru.anyforms.dto.marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Вариант товара (размер/объём) со своей ценой: «80 мл» — 1990, «20 см» — 2490. */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ProductVariantDTO {
    private UUID id;
    private String label;
    private String price;
}
