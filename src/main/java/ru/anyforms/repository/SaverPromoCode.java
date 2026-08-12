package ru.anyforms.repository;

import ru.anyforms.model.payment.PromoCode;

public interface SaverPromoCode {
    PromoCode save(PromoCode promoCode);
}
