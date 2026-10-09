package ru.anyforms.service.payment;

import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.service.promo.PromoClient;

public interface PromoReservationService {

    void releaseOwnReservations(PromoCode promo, PromoClient client);
}
