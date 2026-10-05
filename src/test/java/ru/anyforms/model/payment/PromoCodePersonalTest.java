package ru.anyforms.model.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromoCodePersonalTest {

    @Test
    void publicCodeBelongsToEveryoneInEveryShop() {
        PromoCode promo = PromoCode.builder().code("ANY-10").build();

        assertTrue(promo.belongsTo("a@b.ru", "9991234567"));
        assertTrue(promo.allowedInShop("af_pastry"));
    }

    @Test
    void personalCodeMatchesEmailOrPhone() {
        PromoCode promo = PromoCode.builder().code("SHOP-AAAAA")
                .ownerEmail("buyer@example.com").ownerPhoneLast10("9991234567").shopSlug("anyforms").build();

        assertTrue(promo.belongsTo(" Buyer@Example.com ", ""));
        assertTrue(promo.belongsTo("other@example.com", "9991234567"));
        assertFalse(promo.belongsTo("other@example.com", "9990000000"));
        assertTrue(promo.allowedInShop("anyforms"));
        assertFalse(promo.allowedInShop("af_pastry"));
    }
}
