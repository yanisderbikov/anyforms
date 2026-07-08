package ru.anyforms.repository;

import ru.anyforms.model.payment.PromoCode;

import java.util.Optional;

public interface GetterPromoCode {

    /** Ищет промокод по нормализованному коду (см. {@link PromoCode#normalize}). */
    Optional<PromoCode> getByCode(String code);
}
