package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterPromoCode;

import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class PromoCodeManager implements GetterPromoCode {

    private final PromoCodeRepo promoCodeRepo;

    @Override
    public Optional<PromoCode> getByCode(String code) {
        String normalized = PromoCode.normalize(code);
        if (normalized == null || normalized.isEmpty()) {
            return Optional.empty();
        }
        try {
            return promoCodeRepo.findByCode(normalized);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
