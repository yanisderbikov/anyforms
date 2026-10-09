package ru.anyforms.repository.impl;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.payment.PromoCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface PromoCodeRepo extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCode p where p.code = :code")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);

    @Query("select p from PromoCode p where p.popupId is null and (p.validUntil is null or p.validUntil > :now) order by p.createdAt desc")
    List<PromoCode> findAllNotExpired(@Param("now") Instant now);
}
