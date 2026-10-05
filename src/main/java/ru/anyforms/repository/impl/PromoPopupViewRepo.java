package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.promo.PromoPopupView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface PromoPopupViewRepo extends JpaRepository<PromoPopupView, UUID> {

    long countByPopupIdAndDeviceId(UUID popupId, String deviceId);

    Optional<PromoPopupView> findFirstByPopupIdAndDeviceIdOrderByCreatedAtDesc(UUID popupId, String deviceId);

    @Query("select v.popupId, count(v), count(distinct v.deviceId) from PromoPopupView v group by v.popupId")
    List<Object[]> statsGroupedByPopup();
}
