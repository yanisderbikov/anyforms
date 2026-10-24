package ru.anyforms.model.amo;

import ru.anyforms.model.marketplace.Shop;

public final class AmoContactShop {

    private AmoContactShop() {
    }

    public static String valueFor(Shop shop) {
        if (shop == null) {
            return Shop.DEFAULT_SLUG;
        }
        String value = shop.getAmoContactValue();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
