package ru.anyforms.service.email;

import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
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
                .replace("%ORDER%", esc(payload.getOrderPublicId() == null ? "" : payload.getOrderPublicId()))
                .replace("%ROWS%", buildRows(payload.getItems(), SHOP_ROW_STYLES.getOrDefault(slug, DEFAULT_ROW_STYLE)))
                .replace("%TOTAL%", formatRub(payload.getTotalRub()))
                .replace("%PVZ%", esc(buildPvz(payload)))
                .replace("%CUSTOMER%", esc(payload.getCustomerName() == null ? "" : payload.getCustomerName()))
                .replace("%SUPPORT_TG%", esc(supportTelegram));
    }

    /**
     * Оформление строк чека: строки собираются в коде, поэтому палитра и отступы
     * задаются здесь, а не в шаблоне. У anyforms строки живут в серой рамке-плашке
     * (боковой отступ 22px), у af_pastry — лежат на карточке без рамки, в край.
     */
    private record RowStyle(String border, String name, String qty, String price, String sidePadding) {
    }

    private static final RowStyle DEFAULT_ROW_STYLE = new RowStyle("#ececec", "#111111", "#8c8c8c", "#111111", "22px");

    private static final Map<String, RowStyle> SHOP_ROW_STYLES = Map.of(
            "af_pastry", new RowStyle("#eadfcd", "#4a2e35", "#a08d80", "#4a2e35", "0"));

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
        StringBuilder sb = new StringBuilder();
        if (payload.getPvzCity() != null && !payload.getPvzCity().isBlank()) {
            sb.append(payload.getPvzCity());
        }
        if (payload.getPvzStreet() != null && !payload.getPvzStreet().isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(payload.getPvzStreet());
        }
        return sb.toString();
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
