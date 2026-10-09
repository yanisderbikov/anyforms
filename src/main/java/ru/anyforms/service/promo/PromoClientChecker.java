package ru.anyforms.service.promo;

import ru.anyforms.model.payment.PromoCode;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface PromoClientChecker {

    Duration PENDING_RESERVATION = Duration.ofMinutes(30);

    boolean hasOrders(PromoClient client);

    boolean usedPopup(UUID popupId, PromoClient client);

    boolean usedCode(String code, PromoClient client);

    boolean exhausted(PromoCode promo);

    Optional<String> checkoutRejection(PromoCode promo, PromoClient client, String shopSlug);
}
