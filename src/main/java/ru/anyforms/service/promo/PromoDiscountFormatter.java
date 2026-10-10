package ru.anyforms.service.promo;

import ru.anyforms.util.MoneyUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PromoDiscountFormatter {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"));

    private PromoDiscountFormatter() {
    }

    public static String discount(Integer percent, Long amountKopecks) {
        List<String> parts = new ArrayList<>();
        if (percent != null && percent > 0) {
            parts.add(percent + "%");
        }
        if (amountKopecks != null && amountKopecks > 0) {
            parts.add(MoneyUtil.formatRubles(amountKopecks));
        }
        return String.join(" + ", parts);
    }

    public static String lastValidDay(Instant validUntilExclusive) {
        if (validUntilExclusive == null) {
            return null;
        }
        return DATE.format(validUntilExclusive.minusSeconds(1).atZone(MOSCOW));
    }
}
