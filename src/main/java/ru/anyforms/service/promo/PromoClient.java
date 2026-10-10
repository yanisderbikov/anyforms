package ru.anyforms.service.promo;

import ru.anyforms.util.PhoneUtil;

import java.util.Locale;
import java.util.regex.Pattern;

public record PromoClient(String email, String phoneLast10, String deviceId) {

    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    public static PromoClient of(String email, String phone, String deviceId) {
        return new PromoClient(normalizeEmail(email), PhoneUtil.last10(phone), normalizeDeviceId(deviceId));
    }

    public static String normalizeDeviceId(String deviceId) {
        if (deviceId == null) {
            return "";
        }
        String trimmed = deviceId.trim();
        return DEVICE_ID.matcher(trimmed).matches() ? trimmed : "";
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean hasContact() {
        return !email.isEmpty() || !phoneLast10.isEmpty();
    }

    public boolean hasDevice() {
        return !deviceId.isEmpty();
    }

    public boolean isAnonymous() {
        return !hasContact() && !hasDevice();
    }

    public String deviceIdOrNull() {
        return deviceId.isEmpty() ? null : deviceId;
    }
}
