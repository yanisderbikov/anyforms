package ru.anyforms.dto.marketplace;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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
     * Папка в S3 (под shop/). В ней лежат фото продукта. Необязательна:
     * при загрузке фото из админки создаётся автоматически по id товара.
     * При обновлении пустое значение оставляет текущую папку без изменений.
     */
    private String folder;
    @NotBlank(message = "price обязателен")
    private String price;
    private String crossedPrice;
    private String discountPercent;
    /**
     * Ссылка на Telegram-пост товара. Необязательна; пустая строка при обновлении очищает ссылку.
     */
    private String tgLink;
    private Integer orderNumber;
    private Long amoProductId;
    private String amoProductName;
    /**
     * Доступен ли товар к продаже (показывается на витрине). null при обновлении — не менять.
     */
    private Boolean active;
    /**
     * Товар продаётся по предзаказу (плашка и пояснение на витрине). null при обновлении — не менять.
     */
    private Boolean preorder;
    /**
     * Slug'и магазинов, в которых продаётся товар (anyforms, af_pastry). Набор заменяется целиком:
     * пустой список снимает товар со всех витрин. null при создании — магазин по умолчанию (anyforms),
     * null при обновлении — не менять.
     */
    private List<String> shopSlugs;
    /**
     * Варианты товара (размер/объём — цена). Набор заменяется целиком: пустой список удаляет
     * все варианты, null при обновлении — не менять. Вариант с id обновляется, без id — создаётся.
     */
    private List<ProductVariantRequestDTO> variants;
}
