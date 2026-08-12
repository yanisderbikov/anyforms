package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.PromoCodeDeleter;
import ru.anyforms.repository.SaverPromoCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class PromoCodeManager implements GetterPromoCode, SaverPromoCode, PromoCodeDeleter {

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

    @Override
    public Optional<PromoCode> getById(UUID id) {
        try {
            return promoCodeRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PromoCode> getAllNotExpired(Instant now) {
        try {
            return promoCodeRepo.findAllNotExpired(now);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public PromoCode save(PromoCode promoCode) {
        try {
            return promoCodeRepo.save(promoCode);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        try {
            promoCodeRepo.deleteById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
