package ru.anyforms.service.email;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceOrderEmailDeliveryNoteTest {

    @ParameterizedTest
    @ValueSource(strings = {"anyforms", "af_pastry", "di_gips", "lunasvecha"})
    void freeDeliveryOrderSaysDeliveryIsFree(String shopSlug) {
        String html = EmailTemplate.getMarketplaceOrderEmail(payload(shopSlug, true));

        assertTrue(html.contains("бесплатная"));
        assertFalse(html.contains("оплачивается при&nbsp;получении"));
        assertFalse(html.contains("%DELIVERY_NOTE%"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anyforms", "af_pastry", "di_gips", "lunasvecha"})
    void regularOrderSaysDeliveryIsPaidOnPickup(String shopSlug) {
        String html = EmailTemplate.getMarketplaceOrderEmail(payload(shopSlug, false));

        assertTrue(html.contains("оплачивается при&nbsp;получении"));
        assertFalse(html.contains("%DELIVERY_NOTE%"));
    }

    private static MarketplaceOrderEmailPayload payload(String shopSlug, boolean freeDelivery) {
        return MarketplaceOrderEmailPayload.builder()
                .to("buyer@mail.ru")
                .orderPublicId("A1B2C3")
                .customerName("Иван")
                .pvzCity("Москва")
                .pvzStreet("ул. Ленина, 1")
                .totalRub("12000.00")
                .shopSlug(shopSlug)
                .shopName(shopSlug)
                .freeDelivery(freeDelivery)
                .items(List.of(MarketplaceOrderEmailPayload.Item.builder()
                        .name("Молд").quantity(1).priceRub("12000.00").build()))
                .build();
    }
}
