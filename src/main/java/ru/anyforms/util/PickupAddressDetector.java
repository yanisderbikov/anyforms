package ru.anyforms.util;

import java.util.Set;

public class PickupAddressDetector {

    private static final Set<String> SPB_ONLY = Set.of(
            "санкт петербург", "г санкт петербург", "спб", "г спб", "питер", "петербург", "spb", "saint petersburg"
    );

    public static boolean isPickup(String pvzCity, String pvzStreet) {
        if (mentionsPickup(pvzCity) || mentionsPickup(pvzStreet)) {
            return true;
        }
        return isEmptyish(pvzCity) && isEmptyish(pvzStreet);
    }

    private static boolean mentionsPickup(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("лично") || lower.contains("самовывоз");
    }

    private static boolean isEmptyish(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.matches("[-–—_.]+")) {
            return true;
        }
        String normalized = trimmed.toLowerCase()
                .replaceAll("[^а-яёa-z]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isEmpty() || SPB_ONLY.contains(normalized);
    }
}
