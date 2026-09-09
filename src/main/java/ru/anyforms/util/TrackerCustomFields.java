package ru.anyforms.util;

import java.util.Set;
import java.util.regex.Pattern;

public class TrackerCustomFields {
    public static final Set<String> READY_KEYWORDS = Set.of(
            "готов", "отправ", "забрал", "лично", "готово", "отправлен"
    );

    public static final String INVALID_TRACKER_MESSAGE = "Трекер должен содержать только цифры";

    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");

    public static boolean isValidTracker(String tracker) {
        if (tracker == null) {
            return false;
        }
        String normalized = tracker.trim();
        return DIGITS_ONLY.matcher(normalized).matches() || READY_KEYWORDS.contains(normalized.toLowerCase());
    }
}
