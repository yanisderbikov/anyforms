package ru.anyforms.service.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.dto.promo.CoursePromoDTO;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceOrderEmailCourseOfferTest {

    private static final Pattern UNFILLED_PLACEHOLDER = Pattern.compile("%[A-Z_]+%");

    @ParameterizedTest
    @ValueSource(strings = {"anyforms", "af_pastry", "di_gips", "lunasvecha"})
    void offerIsRenderedWithCodeLinkAndConditions(String shopSlug) {
        String html = EmailTemplate.getMarketplaceOrderEmail(payload(shopSlug), offer("на курс {discount}", "Цена {price} вместо {oldPrice}, {days}, по {until}"));

        assertTrue(html.contains("Подарок к заказу"));
        assertTrue(html.contains("&minus;4 300 ₽"));
        assertTrue(html.contains("RETAIL-SWB5G"));
        assertTrue(html.contains("https://anyforms.ru/course?promo=RETAIL-SWB5G"));
        assertTrue(html.contains("на курс 4 300 ₽"));
        assertTrue(html.contains("Цена 12 600 ₽ вместо 16 900 ₽, 90 дней, по 7 ноября 2026"));
        assertTrue(html.contains("только для номера …9212, действует по 7 ноября 2026"));
        assertTrue(html.contains("Внутри подарок: скидка 4 300 ₽ на курс."));
        assertFalse(UNFILLED_PLACEHOLDER.matcher(html).find());
    }

    @ParameterizedTest
    @ValueSource(strings = {"anyforms", "af_pastry", "di_gips", "lunasvecha"})
    void withoutOfferNoBlockAndNoPlaceholders(String shopSlug) {
        String html = EmailTemplate.getMarketplaceOrderEmail(payload(shopSlug), null);

        assertFalse(html.contains("Подарок к заказу"));
        assertFalse(html.contains("Внутри подарок"));
        assertFalse(UNFILLED_PLACEHOLDER.matcher(html).find());
    }

    @Test
    void popupTextIsEscaped() {
        String html = EmailTemplate.getMarketplaceOrderEmail(payload("anyforms"), offer("<b>курс</b>", null));

        assertTrue(html.contains("&lt;b&gt;курс&lt;/b&gt;"));
        assertFalse(html.contains("<b>курс</b>"));
    }

    @Test
    void anyformsReceiptCountsItemsByQuantity() {
        String html = EmailTemplate.getMarketplaceOrderEmail(payload("anyforms"), null);

        assertTrue(html.contains("Состав заказа · 2 товара"));
    }

    private static CoursePromoDTO offer(String title, String description) {
        return new CoursePromoDTO(UUID.randomUUID(), "Скидка на курс", title, description, "Открыть курс со скидкой",
                "RETAIL-SWB5G", "COURSE_STREAM_2", 430_000L, 1_690_000L, 1_260_000L,
                "2026-11-07T21:00:00Z", 90, "9212", false);
    }

    private static MarketplaceOrderEmailPayload payload(String shopSlug) {
        return MarketplaceOrderEmailPayload.builder()
                .to("buyer@mail.ru")
                .orderPublicId("9MMN3O")
                .customerName("Иван")
                .pvzCity("Алматы")
                .pvzStreet("Кабанбай батыра, 122")
                .totalRub("4300.00")
                .shopSlug(shopSlug)
                .shopName(shopSlug)
                .items(List.of(MarketplaceOrderEmailPayload.Item.builder()
                        .name("мышиный король").quantity(2).priceRub("4300.00").build()))
                .build();
    }
}
