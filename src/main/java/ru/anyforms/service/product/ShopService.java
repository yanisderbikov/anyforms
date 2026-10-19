package ru.anyforms.service.product;

import ru.anyforms.dto.marketplace.AdminShopCreateRequestDTO;
import ru.anyforms.dto.marketplace.AdminShopDTO;
import ru.anyforms.dto.marketplace.AdminShopUpdateRequestDTO;
import ru.anyforms.dto.marketplace.ShopDTO;
import ru.anyforms.model.marketplace.Shop;

import java.util.List;
import java.util.UUID;

public interface ShopService {

    /** Активные магазины — для витрины и селекта в админке. */
    List<ShopDTO> getActiveShops();

    /**
     * Магазин по slug. Пустой slug — магазин по умолчанию (anyforms).
     * Бросает 400/404, если магазин не найден или выключен.
     */
    Shop resolveBySlug(String slug);

    List<AdminShopDTO> getAllShopsForAdmin();

    AdminShopDTO create(AdminShopCreateRequestDTO request);

    AdminShopDTO update(UUID id, AdminShopUpdateRequestDTO request);
}
