package ru.anyforms.service.product;

import ru.anyforms.dto.marketplace.ShopDTO;
import ru.anyforms.model.marketplace.Shop;

import java.util.List;

public interface ShopService {

    /** Активные магазины — для витрины и селекта в админке. */
    List<ShopDTO> getActiveShops();

    /**
     * Магазин по slug. Пустой slug — магазин по умолчанию (anyforms).
     * Бросает 400/404, если магазин не найден или выключен.
     */
    Shop resolveBySlug(String slug);
}
