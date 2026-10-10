package ru.anyforms.repository.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.promo.PromoPopupLead;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface PromoPopupLeadRepo extends JpaRepository<PromoPopupLead, UUID> {

    @Query("""
            select l from PromoPopupLead l
            where l.popupId = :popupId
              and ((:email <> '' and lower(l.email) = lower(:email))
                   or (:phoneLast10 <> '' and l.phoneLast10 = :phoneLast10)
                   or (:deviceId <> '' and l.deviceId = :deviceId))
            order by l.createdAt desc
            """)
    List<PromoPopupLead> findForClient(@Param("popupId") UUID popupId,
                                       @Param("email") String email,
                                       @Param("phoneLast10") String phoneLast10,
                                       @Param("deviceId") String deviceId,
                                       Pageable pageable);

    @Query("""
            select count(l) from PromoPopupLead l
            where l.popupId = :popupId
              and ((:email <> '' and lower(l.email) = lower(:email))
                   or (:phoneLast10 <> '' and l.phoneLast10 = :phoneLast10)
                   or (:deviceId <> '' and l.deviceId = :deviceId))
            """)
    long countForClient(@Param("popupId") UUID popupId,
                        @Param("email") String email,
                        @Param("phoneLast10") String phoneLast10,
                        @Param("deviceId") String deviceId);

    Optional<PromoPopupLead> findFirstByPopupIdAndOrderIdOrderByCreatedAtDesc(UUID popupId, Long orderId);

    List<PromoPopupLead> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PromoPopupLead> findByPopupIdOrderByCreatedAtDesc(UUID popupId, Pageable pageable);

    @Query("select l.popupId, count(l) from PromoPopupLead l group by l.popupId")
    List<Object[]> countGroupedByPopup();
}
