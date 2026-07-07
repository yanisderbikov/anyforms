package ru.anyforms.service.email;

import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;

import java.util.List;

public final class EmailTemplate {

    private EmailTemplate() {
    }

    public static String getGuideEmail(String link) {
        return load("templates/email-guide.html").replace("%LINK%", link);
    }

    public static String getCourseEmail(String link) {
        return load("templates/email-course.html").replace("%LINK%", link);
    }

    /**
     * Письмо-чек заказа маркетплейса: таблица позиций, итог, адрес ПВЗ, данные получателя.
     */
    public static String getMarketplaceOrderEmail(MarketplaceOrderEmailPayload payload) {
        return load("templates/email-marketplace-order.html")
                .replace("%ORDER%", esc(payload.getOrderPublicId() == null ? "" : payload.getOrderPublicId()))
                .replace("%ROWS%", buildRows(payload.getItems()))
                .replace("%TOTAL%", formatRub(payload.getTotalRub()))
                .replace("%PVZ%", esc(buildPvz(payload)))
                .replace("%CUSTOMER%", esc(payload.getCustomerName() == null ? "" : payload.getCustomerName()));
    }

    private static String buildRows(List<MarketplaceOrderEmailPayload.Item> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MarketplaceOrderEmailPayload.Item item : items) {
            int qty = item.getQuantity() == null ? 1 : item.getQuantity();
            sb.append("<tr>")
                    .append("<td class=\"font\" style=\"padding:12px 22px; font-size:15px; line-height:1.4; color:#111111; border-top:1px solid #ececec;\">")
                    .append(esc(item.getName())).append("</td>")
                    .append("<td class=\"font\" align=\"center\" style=\"padding:12px 10px; font-size:15px; color:#8c8c8c; border-top:1px solid #ececec; white-space:nowrap;\">×")
                    .append(qty).append("</td>")
                    .append("<td class=\"font\" align=\"right\" style=\"padding:12px 22px; font-size:15px; color:#111111; border-top:1px solid #ececec; white-space:nowrap;\">")
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
        try (var stream = EmailTemplate.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (stream == null) {
                throw new IllegalStateException("Шаблон письма не найден: " + templatePath);
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Не получилось использовать шаблон письма: " + templatePath, e);
        }
    }
}
