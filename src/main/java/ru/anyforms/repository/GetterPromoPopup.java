package ru.anyforms.repository;

import ru.anyforms.model.promo.PromoPopup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterPromoPopup {

    Optional<PromoPopup> getById(UUID id);

    List<PromoPopup> getAll();

    List<PromoPopup> getLive(String shopSlug, Instant now);

    List<PromoPopup> getByPromoCodeId(UUID promoCodeId);
}
