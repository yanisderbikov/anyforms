package ru.anyforms.dto.marketplace;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateUpdateRequestDTO {
    /**
     * Если передан — обновляем продукт с этим id. Иначе создаём новый.
     */
    private UUID id;
    @NotBlank(message = "name обязателен")
    private String name;
    @NotBlank(message = "description обязателен")
    private String description;
    /**
     * Папка в S3 (под shop/). В ней лежат фото продукта.
     */
    @NotBlank(message = "folder обязателен")
    private String folder;
    @NotBlank(message = "price обязателен")
    private String price;
    private String crossedPrice;
    private String discountPercent;
    @NotBlank(message = "tgLink обязателен")
    private String tgLink;
    private Integer orderNumber;
}
