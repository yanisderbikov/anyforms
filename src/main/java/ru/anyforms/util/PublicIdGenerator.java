package ru.anyforms.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

public final class PublicIdGenerator {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int LENGTH = 6;
    private static final int MAX_ATTEMPTS = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicIdGenerator() {
    }

    public static String generateUnique(Predicate<String> exists) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
            String candidate = sb.toString();
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный публичный номер");
    }
}
