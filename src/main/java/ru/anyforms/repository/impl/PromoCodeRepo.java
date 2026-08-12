package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("select p from PromoCode p where p.validUntil is null or p.validUntil > :now order by p.createdAt desc")
    List<PromoCode> findAllNotExpired(@Param("now") Instant now);
}
