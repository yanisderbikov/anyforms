package ru.anyforms.util;

public final class PhoneUtil {

    private PhoneUtil() {
    }

    public static String toE164(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "7" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("8")) {
            return "7" + digits.substring(1);
        }
        if (digits.length() >= 11 && digits.length() <= 15) {
            return digits;
        }
        return null;
    }
}
