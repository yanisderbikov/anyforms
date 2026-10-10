package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.promo.PromoPopup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
interface PromoPopupRepo extends JpaRepository<PromoPopup, UUID> {

    List<PromoPopup> findAllByOrderByCreatedAtDesc();

    List<PromoPopup> findByPromoCodeId(UUID promoCodeId);

    @Query("""
            select p from PromoPopup p
            where p.active = true
              and p.shopSlug = :shopSlug
              and (p.validFrom is null or p.validFrom <= :now)
              and (p.validUntil is null or p.validUntil > :now)
            order by p.priority desc, p.createdAt desc
            """)
    List<PromoPopup> findLive(@Param("shopSlug") String shopSlug, @Param("now") Instant now);
}
