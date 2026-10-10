package ru.anyforms.model.amo;

import ru.anyforms.model.marketplace.Shop;

import java.util.Set;

public final class AmoContactShop {

    private static final Set<String> KNOWN = Set.of("anyforms", "di_gips", "lunasvecha");

    private AmoContactShop() {
    }

    public static String valueFor(Shop shop) {
        String slug = shop == null || shop.getSlug() == null || shop.getSlug().isBlank()
                ? Shop.DEFAULT_SLUG
                : shop.getSlug().trim().toLowerCase();
        return KNOWN.contains(slug) ? slug : null;
    }
}
