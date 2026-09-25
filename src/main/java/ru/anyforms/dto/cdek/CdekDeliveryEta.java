package ru.anyforms.dto.cdek;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public record CdekDeliveryEta(int daysMin, int daysMax, LocalDate plannedDate) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static CdekDeliveryEta ofPlannedDate(LocalDate plannedDate, LocalDate today) {
        if (plannedDate == null) {
            return null;
        }
        int days = (int) Math.max(ChronoUnit.DAYS.between(today, plannedDate), 0);
        return new CdekDeliveryEta(days, days, plannedDate);
    }

    public static CdekDeliveryEta ofPeriod(Integer periodMin, Integer periodMax) {
        if (periodMin == null && periodMax == null) {
            return null;
        }
        int min = periodMin != null ? periodMin : periodMax;
        int max = periodMax != null ? periodMax : periodMin;
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        return new CdekDeliveryEta(Math.max(min, 0), Math.max(max, 0), null);
    }

    public String daysText() {
        if (daysMin == daysMax) {
            return daysMin + " " + daysWord(daysMin);
        }
        return daysMin + "-" + daysMax + " " + daysWord(daysMax);
    }

    public String describe() {
        if (plannedDate == null) {
            return daysText();
        }
        if (daysMax <= 0) {
            return "сегодня (" + plannedDate.format(DATE) + ")";
        }
        return daysText() + " (до " + plannedDate.format(DATE) + ")";
    }

    static String daysWord(int days) {
        int mod100 = days % 100;
        int mod10 = days % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "дней";
        }
        if (mod10 == 1) {
            return "день";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "дня";
        }
        return "дней";
    }
}
