package ru.anyforms.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PhotoOrderUtil {

    private static final String SEPARATOR = "\n";

    private PhotoOrderUtil() {
    }

    public static List<String> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String line : stored.split(SEPARATOR)) {
            String name = line.trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    public static String join(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String name : fileNames) {
            String trimmed = name == null ? "" : name.trim();
            if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        return cleaned.isEmpty() ? null : String.join(SEPARATOR, cleaned);
    }

    public static List<String> applyOrder(List<String> urls, String stored) {
        List<String> order = parse(stored);
        if (urls == null || urls.isEmpty() || order.isEmpty()) {
            return urls == null ? List.of() : urls;
        }
        Map<String, Integer> position = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) {
            position.put(order.get(i), i);
        }
        List<String> sorted = new ArrayList<>(urls);
        sorted.sort(Comparator.comparingInt(url -> position.getOrDefault(fileName(url), Integer.MAX_VALUE)));
        return sorted;
    }

    public static String fileName(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String withoutQuery = url;
        int query = withoutQuery.indexOf('?');
        if (query >= 0) {
            withoutQuery = withoutQuery.substring(0, query);
        }
        int slash = withoutQuery.lastIndexOf('/');
        String name = slash >= 0 ? withoutQuery.substring(slash + 1) : withoutQuery;
        return URLDecoder.decode(name, StandardCharsets.UTF_8);
    }
}
