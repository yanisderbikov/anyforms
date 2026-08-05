package ru.anyforms.dto.marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Магазин витрины: anyforms и партнёрские магазины со своей страницей /shop/{slug}. */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ShopDTO {
    private UUID id;
    private String slug;
    private String name;
    private Boolean active;
    private String supportTelegram;
}
