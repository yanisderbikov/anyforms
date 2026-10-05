package ru.anyforms.repository;

import ru.anyforms.model.promo.PromoPopupLead;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetterPromoPopupLead {

    Optional<PromoPopupLead> getById(UUID id);

    Optional<PromoPopupLead> getLatestForClient(UUID popupId, String email, String phoneLast10, String deviceId);

    List<PromoPopupLead> getRecent(UUID popupId, int limit);

    Map<UUID, Long> countByPopup();
}
