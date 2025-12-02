package ru.anyforms.util;

import lombok.extern.log4j.Log4j2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class LeadUrlExtractor {

    private static final Pattern LEAD_ID_PATTERN = Pattern.compile("leads/detail/(\\d+)");

    public static Long extract(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = LEAD_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Не удалось преобразовать ID сделки в число: {}", matcher.group(1));
                return null;
            }
        }

        // Пробуем найти ID в конце URL, если паттерн не сработал
        String[] parts = url.split("/");
        if (parts.length > 0) {
            try {
                String lastPart = parts[parts.length - 1];
                // Убираем возможные параметры запроса
                if (lastPart.contains("?")) {
                    lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                }
                return Long.parseLong(lastPart);
            } catch (NumberFormatException e) {
                // Игнорируем, пробуем другой способ
            }
        }

        return null;
    }
}
