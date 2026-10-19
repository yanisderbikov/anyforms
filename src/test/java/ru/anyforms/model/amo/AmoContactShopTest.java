package ru.anyforms.model.amo;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.marketplace.Shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AmoContactShopTest {

    private static Shop shop(String slug, String amoContactValue) {
        Shop shop = new Shop();
        shop.setSlug(slug);
        shop.setName(slug);
        shop.setAmoContactValue(amoContactValue);
        return shop;
    }

    @Test
    void shopWithAmoValueUsesIt() {
        assertEquals("anyforms", AmoContactShop.valueFor(shop("anyforms", "anyforms")));
        assertEquals("di_gips", AmoContactShop.valueFor(shop("di_gips", "di_gips")));
        assertEquals("lunasvecha", AmoContactShop.valueFor(shop("lunasvecha", " lunasvecha ")));
    }

    @Test
    void missingShopIsAnyforms() {
        assertEquals("anyforms", AmoContactShop.valueFor(null));
    }

    @Test
    void shopWithoutAmoValueGivesNull() {
        assertNull(AmoContactShop.valueFor(shop("af_pastry", null)));
        assertNull(AmoContactShop.valueFor(shop("new_shop", " ")));
    }
}
