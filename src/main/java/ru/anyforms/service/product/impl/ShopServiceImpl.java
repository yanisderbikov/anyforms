package ru.anyforms.service.product.impl;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.marketplace.ShopDTO;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.repository.GetterShop;
import ru.anyforms.service.product.ShopService;

import java.util.List;

@Service
@AllArgsConstructor
class ShopServiceImpl implements ShopService {

    private final GetterShop getterShop;

    @Override
    public List<ShopDTO> getActiveShops() {
        return getterShop.getActiveShops().stream()
                .map(shop -> new ShopDTO(shop.getId(), shop.getSlug(), shop.getName(), shop.getActive(),
                        shop.getSupportTelegram()))
                .toList();
    }

    @Override
    public Shop resolveBySlug(String slug) {
        String normalized = slug == null || slug.isBlank() ? Shop.DEFAULT_SLUG : slug.trim();
        Shop shop = getterShop.getBySlug(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден: " + normalized));
        if (Boolean.FALSE.equals(shop.getActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин отключён: " + normalized);
        }
        return shop;
    }
}
