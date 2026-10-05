package ru.anyforms.service.promo;

import ru.anyforms.model.payment.PromoCode;

import java.util.Optional;
import java.util.UUID;

public interface PromoClientChecker {

    boolean hasOrders(PromoClient client);

    boolean usedPopup(UUID popupId, PromoClient client);

    boolean usedCode(String code, PromoClient client);

    boolean exhausted(PromoCode promo, PromoClient client);

    Optional<String> checkoutRejection(PromoCode promo, PromoClient client, String shopSlug);
}
