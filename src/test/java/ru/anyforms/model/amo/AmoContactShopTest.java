package ru.anyforms.model.amo;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.marketplace.Shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AmoContactShopTest {

    private static Shop shop(String slug) {
        Shop shop = new Shop();
        shop.setSlug(slug);
        shop.setName(slug);
        return shop;
    }

    @Test
    void knownShopsMapToEnumValues() {
        assertEquals("anyforms", AmoContactShop.valueFor(shop("anyforms")));
        assertEquals("di_gips", AmoContactShop.valueFor(shop("di_gips")));
        assertEquals("lunasvecha", AmoContactShop.valueFor(shop(" LunaSvecha ")));
    }

    @Test
    void missingShopIsAnyforms() {
        assertEquals("anyforms", AmoContactShop.valueFor(null));
        assertEquals("anyforms", AmoContactShop.valueFor(shop(" ")));
    }

    @Test
    void unknownShopGivesNull() {
        assertNull(AmoContactShop.valueFor(shop("af_pastry")));
    }
}
