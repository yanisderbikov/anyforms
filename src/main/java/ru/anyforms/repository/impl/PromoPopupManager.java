package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.PromoPopupDeleter;
import ru.anyforms.repository.SaverPromoPopup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class PromoPopupManager implements GetterPromoPopup, SaverPromoPopup, PromoPopupDeleter {

    private final PromoPopupRepo promoPopupRepo;

    @Override
    public Optional<PromoPopup> getById(UUID id) {
        try {
            return promoPopupRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PromoPopup> getAll() {
        try {
            return promoPopupRepo.findAllByOrderByCreatedAtDesc();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PromoPopup> getLive(String shopSlug, Instant now) {
        try {
            return promoPopupRepo.findLive(shopSlug, now);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PromoPopup> getByPromoCodeId(UUID promoCodeId) {
        try {
            return promoPopupRepo.findByPromoCodeId(promoCodeId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public PromoPopup save(PromoPopup popup) {
        try {
            return promoPopupRepo.save(popup);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        try {
            promoPopupRepo.deleteById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
