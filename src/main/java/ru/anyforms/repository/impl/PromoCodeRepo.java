package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.payment.PromoCode;

import java.util.Optional;
import java.util.UUID;

@Repository
interface PromoCodeRepo extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCode(String code);
}
