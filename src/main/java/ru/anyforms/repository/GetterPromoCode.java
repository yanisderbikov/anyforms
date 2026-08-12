package ru.anyforms.repository;

import ru.anyforms.model.payment.PromoCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterPromoCode {

    /** Ищет промокод по нормализованному коду (см. {@link PromoCode#normalize}). */
    Optional<PromoCode> getByCode(String code);

    Optional<PromoCode> getById(UUID id);

    /** Промокоды, срок которых не истёк на {@code now} (или бессрочные). Новые сверху. */
    List<PromoCode> getAllNotExpired(Instant now);
}
