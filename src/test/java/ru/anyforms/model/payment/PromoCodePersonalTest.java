package ru.anyforms.model.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromoCodePersonalTest {

    private static final String DEVICE = "6f1c7a52-8b1e-4c39-9d7e-0a2b3c4d5e6f";
    private static final String OTHER_DEVICE = "0aa1bb2c-3dd4-4ee5-8ff6-112233445566";

    @Test
    void publicCodeBelongsToEveryoneInEveryShop() {
        PromoCode promo = PromoCode.builder().code("ANY-10").build();

        assertFalse(promo.isPersonal());
        assertTrue(promo.belongsTo("a@b.ru", "9991234567", DEVICE));
        assertTrue(promo.belongsTo("", "", ""));
        assertTrue(promo.allowedInShop("af_pastry"));
    }

    @Test
    void personalCodeMatchesEmailOrPhone() {
        PromoCode promo = PromoCode.builder().code("SHOP-AAAAA")
                .ownerEmail("buyer@example.com").ownerPhoneLast10("9991234567").shopSlug("anyforms").build();

        assertTrue(promo.isPersonal());
        assertTrue(promo.hasContactOwner());
        assertTrue(promo.belongsTo(" Buyer@Example.com ", "", ""));
        assertTrue(promo.belongsTo("other@example.com", "9991234567", ""));
        assertFalse(promo.belongsTo("other@example.com", "9990000000", ""));
        assertTrue(promo.allowedInShop("anyforms"));
        assertFalse(promo.allowedInShop("af_pastry"));
    }

    @Test
    void contactCodeAlsoWorksFromAnotherDeviceOfTheOwner() {
        PromoCode promo = PromoCode.builder().code("SHOP-AAAAA")
                .ownerEmail("buyer@example.com").ownerDeviceId(DEVICE).build();

        assertTrue(promo.belongsTo("buyer@example.com", "", OTHER_DEVICE));
        assertTrue(promo.belongsTo("other@example.com", "", DEVICE));
        assertFalse(promo.belongsTo("other@example.com", "", OTHER_DEVICE));
    }

    @Test
    void deviceOnlyCodeBelongsToItsDevice() {
        PromoCode promo = PromoCode.builder().code("ONE-AAAAA").ownerDeviceId(DEVICE).build();

        assertTrue(promo.isPersonal());
        assertFalse(promo.hasContactOwner());
        assertTrue(promo.belongsTo("anyone@example.com", "9991234567", DEVICE));
        assertFalse(promo.belongsTo("anyone@example.com", "9991234567", OTHER_DEVICE));
        assertFalse(promo.belongsTo("anyone@example.com", "9991234567", ""));
        assertFalse(promo.belongsTo(null, null, null));
    }

    @Test
    void blankOwnerIdentifiersDoNotBindTheCode() {
        PromoCode promo = PromoCode.builder().code("ANY-10").ownerEmail("").ownerPhoneLast10(" ").ownerDeviceId("").build();

        assertFalse(promo.isPersonal());
        assertTrue(promo.belongsTo("", "", ""));
    }
}
