package ru.anyforms.service.promo.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterPromoCode;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
class PromoCodeGenerator {

    static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_RANDOM_LENGTH = 5;
    private static final int CODE_ATTEMPTS = 20;

    private final GetterPromoCode getterPromoCode;
    private final SecureRandom random = new SecureRandom();

    String generate(String prefix) {
        String normalizedPrefix = PromoCode.normalize(prefix);
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(normalizedPrefix).append('-');
            for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (getterPromoCode.getByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный промокод с префиксом " + normalizedPrefix);
    }
}
