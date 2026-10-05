package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.promo.PromoPopupView;
import ru.anyforms.repository.GetterPromoPopupView;
import ru.anyforms.repository.PopupViewStats;
import ru.anyforms.repository.SaverPromoPopupView;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class PromoPopupViewManager implements GetterPromoPopupView, SaverPromoPopupView {

    private final PromoPopupViewRepo promoPopupViewRepo;

    @Override
    public long countForDevice(UUID popupId, String deviceId) {
        try {
            return promoPopupViewRepo.countByPopupIdAndDeviceId(popupId, deviceId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<Instant> lastViewAt(UUID popupId, String deviceId) {
        try {
            return promoPopupViewRepo.findFirstByPopupIdAndDeviceIdOrderByCreatedAtDesc(popupId, deviceId)
                    .map(PromoPopupView::getCreatedAt);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Map<UUID, PopupViewStats> statsByPopup() {
        try {
            Map<UUID, PopupViewStats> stats = new HashMap<>();
            for (Object[] row : promoPopupViewRepo.statsGroupedByPopup()) {
                stats.put((UUID) row[0], new PopupViewStats(((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
            }
            return stats;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public PromoPopupView save(PromoPopupView view) {
        try {
            return promoPopupViewRepo.save(view);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
