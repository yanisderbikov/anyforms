package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.SaverPromoPopupLead;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class PromoPopupLeadManager implements GetterPromoPopupLead, SaverPromoPopupLead {

    private final PromoPopupLeadRepo promoPopupLeadRepo;

    @Override
    public Optional<PromoPopupLead> getById(UUID id) {
        try {
            return promoPopupLeadRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<PromoPopupLead> getLatestForClient(UUID popupId, String email, String phoneLast10,
                                                       String deviceId) {
        try {
            return promoPopupLeadRepo.findForClient(popupId, email == null ? "" : email,
                    phoneLast10 == null ? "" : phoneLast10, deviceId == null ? "" : deviceId,
                    PageRequest.of(0, 1)).stream().findFirst();
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PromoPopupLead> getRecent(UUID popupId, int limit) {
        try {
            PageRequest page = PageRequest.of(0, limit);
            return popupId == null
                    ? promoPopupLeadRepo.findAllByOrderByCreatedAtDesc(page)
                    : promoPopupLeadRepo.findByPopupIdOrderByCreatedAtDesc(popupId, page);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Map<UUID, Long> countByPopup() {
        try {
            Map<UUID, Long> counts = new HashMap<>();
            for (Object[] row : promoPopupLeadRepo.countGroupedByPopup()) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
            return counts;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public PromoPopupLead save(PromoPopupLead lead) {
        try {
            return promoPopupLeadRepo.save(lead);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
