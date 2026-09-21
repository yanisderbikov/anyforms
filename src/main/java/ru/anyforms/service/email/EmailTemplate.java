package ru.anyforms.service.email;

import ru.anyforms.dto.email.DeliveryStatusEmailPayload;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.marketplace.Shop;

import java.util.List;
import java.util.Map;

public final class EmailTemplate {

    private EmailTemplate() {
    }

    /** Письмо после покупки гайда: основной файл + бонусные материалы. */
    public static String getGuideEmail(String link, String bonusLink) {
        return load("templates/email-guide.html")
                .replace("%LINK%", link)
                .replace("%BONUS_LINK%", bonusLink);
    }

    public static String getLoginCodeEmail(String code, long ttlMinutes) {
        return load("templates/email-login-code.html")
                .replace("%CODE%", esc(code))
                .replace("%TTL%", String.valueOf(ttlMinutes));
    }

    public static String getCourseEmail(String link) {
        return load("templates/email-course.html").replace("%LINK%", link);
    }

    /** Курс, тариф «Личное ведение»: то же письмо, но с полным составом тарифа. */
    public static String getCoursePersonalEmail(String link) {
        return load("templates/email-course-personal.html").replace("%LINK%", link);
    }

    private static final String RECEIPT_PREVIEW_BLOCK = """
                            <tr>
                                <td class="pad" style="padding:0 40px 28px 40px;">
                                    <a href="%LINK%"><img src="%PREVIEW_IMG%" alt="Чек" style="display:block; width:100%; max-width:480px; height:auto; border:1px solid #e5e3dc; border-radius:14px;"></a>
                                </td>
                            </tr>""";

    /** Письмо со ссылкой на чек Юкассы; previewImageUrl — картинка-превью чека, может быть null. */
    public static String getReceiptEmail(String link, String previewImageUrl) {
        String preview = previewImageUrl == null
                ? ""
                : RECEIPT_PREVIEW_BLOCK.replace("%PREVIEW_IMG%", esc(previewImageUrl));
        return load("templates/email-receipt.html")
                .replace("%PREVIEW%", preview)
                .replace("%LINK%", link);
    }

    /**
     * Письмо-чек заказа маркетплейса: таблица позиций, итог, адрес ПВЗ, данные получателя.
     * Партнёрский магазин со своим шаблоном templates/email-marketplace-order-{slug}.html
     * получает письмо в своём стиле, остальные — общий шаблон anyforms.
     * Ссылка поддержки — бот магазина заказа; старые таски без полей получают вариант anyforms.
     */
    public static String getMarketplaceOrderEmail(MarketplaceOrderEmailPayload payload) {
        String supportTelegram = payload.getSupportTelegram() == null || payload.getSupportTelegram().isBlank()
                ? Shop.DEFAULT_SUPPORT_TELEGRAM
                : payload.getSupportTelegram();
        String slug = normalizeSlug(payload.getShopSlug());
        return loadMarketplaceOrderTemplate(slug)
                .replace("%ORDER%", esc(payload.getOrderPublicId() == null ? "" : payload.getOrderPublicId().toUpperCase()))
                .replace("%ROWS%", buildRows(payload.getItems(), SHOP_ROW_STYLES.getOrDefault(slug, DEFAULT_ROW_STYLE)))
                .replace("%TOTAL%", formatRub(payload.getTotalRub()))
                .replace("%PVZ%", esc(buildPvz(payload)))
                .replace("%CUSTOMER%", esc(payload.getCustomerName() == null ? "" : payload.getCustomerName()))
                .replace("%SUPPORT_TG%", esc(supportTelegram));
    }

    /**
     * Оформление строк чека: строки собираются в коде, поэтому палитра и отступы
     * задаются здесь, а не в шаблоне. У anyforms строки живут в серой рамке-плашке
     * (боковой отступ 22px), у партнёрских (af_pastry, di_gips, lunasvecha) — лежат
     * на карточке без рамки, в край.
     */
    private record RowStyle(String border, String name, String qty, String price, String sidePadding) {
    }

    private static final RowStyle DEFAULT_ROW_STYLE = new RowStyle("#ececec", "#111111", "#8c8c8c", "#111111", "22px");

    private static final Map<String, RowStyle> SHOP_ROW_STYLES = Map.of(
            "af_pastry", new RowStyle("#eadfcd", "#4a2e35", "#a08d80", "#4a2e35", "0"),
            "di_gips", new RowStyle("#f2dfe6", "#5f4c45", "#b39a8f", "#b44e70", "0"),
            "lunasvecha", new RowStyle("#e7e0d2", "#4a3c32", "#a89c90", "#4a3423", "0"));

    private static String normalizeSlug(String slug) {
        return slug != null && slug.matches("[a-z0-9_-]+") ? slug : Shop.DEFAULT_SLUG;
    }

    private static String loadMarketplaceOrderTemplate(String slug) {
        if (!Shop.DEFAULT_SLUG.equals(slug)) {
            String shopTemplate = loadOptional("templates/email-marketplace-order-" + slug + ".html");
            if (shopTemplate != null) {
                return shopTemplate;
            }
        }
        return load("templates/email-marketplace-order.html");
    }

    private static String buildRows(List<MarketplaceOrderEmailPayload.Item> items, RowStyle style) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MarketplaceOrderEmailPayload.Item item : items) {
            int qty = item.getQuantity() == null ? 1 : item.getQuantity();
            sb.append("<tr>")
                    .append("<td class=\"font\" style=\"padding:12px ").append(style.sidePadding())
                    .append("; font-size:15px; line-height:1.4; color:")
                    .append(style.name()).append("; border-top:1px solid ").append(style.border()).append(";\">")
                    .append(esc(item.getName())).append("</td>")
                    .append("<td class=\"font\" align=\"center\" style=\"padding:12px 10px; font-size:15px; color:")
                    .append(style.qty()).append("; border-top:1px solid ").append(style.border()).append("; white-space:nowrap;\">×")
                    .append(qty).append("</td>")
                    .append("<td class=\"font\" align=\"right\" style=\"padding:12px ").append(style.sidePadding())
                    .append("; font-size:15px; color:")
                    .append(style.price()).append("; border-top:1px solid ").append(style.border()).append("; white-space:nowrap;\">")
                    .append(formatRub(item.getPriceRub())).append("&nbsp;&#8381;</td>")
                    .append("</tr>");
        }
        return sb.toString();
    }

    private static String buildPvz(MarketplaceOrderEmailPayload payload) {
        return joinPvz(payload.getPvzCity(), payload.getPvzStreet());
    }

    private static String joinPvz(String city, String street) {
        StringBuilder sb = new StringBuilder();
        if (city != null && !city.isBlank()) {
            sb.append(city);
        }
        if (street != null && !street.isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(street);
        }
        return sb.toString();
    }

    private static final String CDEK_TRACKING_URL = "https://www.cdek.ru/ru/tracking?order_id=";

    private static final String DETAILS_BLOCK = """
                <tr>
                    <td class="pad" style="padding:0 40px 8px 40px;">
                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#fafafa; border:1px solid #ececec; border-radius:12px;">
                            %ROWS%
                        </table>
                    </td>
                </tr>""";

    private static final String DETAIL_ROW = """
                            <tr>
                                <td class="font" style="padding:18px 22px 6px 22px; font-size:12px; line-height:1.5; color:#8c8c8c; text-transform:lowercase; letter-spacing:0.5px;">%LABEL%</td>
                            </tr>
                            <tr>
                                <td class="font" style="padding:0 22px 18px 22px; font-size:16px; line-height:1.5; font-weight:600; color:#111111;">%VALUE%</td>
                            </tr>""";

    private static final String NEXT_BLOCK = """
                <tr>
                    <td class="pad font" style="padding:20px 40px 24px 40px; font-size:16px; line-height:1.6; color:#333333;">
                        %TEXT%
                    </td>
                </tr>""";

    public static String getDeliveryStatusSubject(DeliveryNotification notification, String orderPublicId) {
        String order = "#" + upper(orderPublicId);
        return switch (notification) {
            case SHIPPED -> "Заказ " + order + " передан в СДЭК";
            case ARRIVED_AT_PVZ -> "Заказ " + order + " ждёт вас в пункте выдачи";
            case READY_FOR_PICKUP -> "Заказ " + order + " готов к выдаче";
        };
    }

    public static String getDeliveryStatusEmail(DeliveryStatusEmailPayload payload, String supportPhone) {
        String supportTelegram = payload.getSupportTelegram() == null || payload.getSupportTelegram().isBlank()
                ? Shop.DEFAULT_SUPPORT_TELEGRAM
                : payload.getSupportTelegram();
        String order = esc(upper(payload.getOrderPublicId()));
        String greeting = payload.getCustomerName() == null || payload.getCustomerName().isBlank()
                ? "Здравствуйте!"
                : "Здравствуйте, " + esc(payload.getCustomerName()) + "!";
        String tracker = payload.getTracker() == null || payload.getTracker().isBlank() ? null : payload.getTracker().trim();
        String pvz = joinPvz(payload.getPvzCity(), payload.getPvzStreet());
        String trackingLink = tracker == null ? "https://anyforms.ru/shop" : CDEK_TRACKING_URL + esc(tracker);

        String title;
        String preheader;
        String intro;
        String details;
        String next;
        String ctaText;
        String ctaLink;
        switch (payload.getNotification()) {
            case SHIPPED -> {
                title = "заказ&nbsp;<span style=\"text-transform:uppercase;\">#" + order + "</span> отправлен";
                preheader = "Посылка передана в СДЭК. Трек-номер и адрес пункта выдачи — внутри.";
                intro = greeting + " Ваш заказ передан в&nbsp;СДЭК и&nbsp;уже едет в&nbsp;пункт выдачи. Ниже трек-номер для&nbsp;отслеживания.";
                String eta = payload.getDeliveryEta() == null || payload.getDeliveryEta().isBlank() ? null : esc(payload.getDeliveryEta());
                details = detailRow("трек-номер СДЭК", tracker)
                        + detailRow("ориентировочный срок доставки", eta)
                        + detailRow("пункт выдачи", pvz);
                next = "Когда посылка приедет в&nbsp;пункт выдачи, мы&nbsp;пришлём ещё одно письмо. Доставка оплачивается при&nbsp;получении.";
                ctaText = "Отследить посылку";
                ctaLink = trackingLink;
            }
            case ARRIVED_AT_PVZ -> {
                title = "заказ&nbsp;<span style=\"text-transform:uppercase;\">#" + order + "</span> приехал";
                preheader = "Посылка уже в пункте выдачи СДЭК — можно забирать.";
                intro = greeting + " Ваша посылка приехала в&nbsp;пункт выдачи СДЭК — можно забирать. Для&nbsp;получения назовите трек-номер или номер телефона получателя.";
                details = detailRow("пункт выдачи", pvz) + detailRow("трек-номер СДЭК", tracker);
                next = "Доставка оплачивается при&nbsp;получении. Срок хранения посылки в&nbsp;пункте выдачи ограничен — постарайтесь забрать её в&nbsp;ближайшие дни.";
                ctaText = "Отследить посылку";
                ctaLink = trackingLink;
            }
            case READY_FOR_PICKUP -> {
                title = "заказ&nbsp;<span style=\"text-transform:uppercase;\">#" + order + "</span> готов";
                preheader = "Заказ собран и ждёт самовывоза.";
                intro = greeting + " Ваш заказ собран и&nbsp;готов к&nbsp;самовывозу. Напишите нам, чтобы договориться о&nbsp;времени и&nbsp;месте получения.";
                details = "";
                next = "";
                ctaText = "Написать в Telegram";
                ctaLink = "https://t.me/" + esc(supportTelegram);
            }
            default -> throw new IllegalArgumentException("Неизвестный тип уведомления о доставке: " + payload.getNotification());
        }

        return load("templates/email-delivery-status.html")
                .replace("%SUBJECT%", esc(getDeliveryStatusSubject(payload.getNotification(), payload.getOrderPublicId())))
                .replace("%PREHEADER%", preheader)
                .replace("%TITLE%", title)
                .replace("%INTRO%", intro)
                .replace("%DETAILS%", details.isEmpty() ? "" : DETAILS_BLOCK.replace("%ROWS%", details))
                .replace("%NEXT%", next.isEmpty() ? "" : NEXT_BLOCK.replace("%TEXT%", next))
                .replace("%CTA_TEXT%", ctaText)
                .replace("%CTA_LINK%", ctaLink)
                .replace("%SUPPORT_PHONE_BLOCK%", supportPhoneBlock(supportPhone))
                .replace("%SUPPORT_TG%", esc(supportTelegram))
                .replace("%ORDER%", order);
    }

    private static String detailRow(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return DETAIL_ROW.replace("%LABEL%", label).replace("%VALUE%", esc(value));
    }

    private static String supportPhoneBlock(String phone) {
        if (phone == null || phone.isBlank()) {
            return ".";
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        return " или по&nbsp;телефону <a href=\"tel:" + esc(digits) + "\" style=\"color:#111111; font-weight:700; text-decoration:underline; white-space:nowrap;\">"
                + esc(formatPhone(digits)) + "</a> — он&nbsp;же в&nbsp;Max, WhatsApp и&nbsp;Telegram.";
    }

    private static String formatPhone(String digits) {
        if (!digits.matches("\\+7\\d{10}")) {
            return digits;
        }
        return "+7 " + digits.substring(2, 5) + " " + digits.substring(5, 8) + "-" + digits.substring(8, 10) + "-" + digits.substring(10);
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    /** "890.00" → "890", "1890.50" → "1890.50". */
    private static String formatRub(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith(".00") ? value.substring(0, value.length() - 3) : value;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String load(String templatePath) {
        String template = loadOptional(templatePath);
        if (template == null) {
            throw new IllegalStateException("Шаблон письма не найден: " + templatePath);
        }
        return template;
    }

    private static String loadOptional(String templatePath) {
        try (var stream = EmailTemplate.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (stream == null) {
                return null;
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Не получилось использовать шаблон письма: " + templatePath, e);
        }
    }
}
