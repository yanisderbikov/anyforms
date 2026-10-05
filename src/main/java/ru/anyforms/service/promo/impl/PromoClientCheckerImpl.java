package ru.anyforms.service.promo.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.promo.PromoClient;
import ru.anyforms.service.promo.PromoClientChecker;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PromoClientCheckerImpl implements PromoClientChecker {

    static final Duration PENDING_RESERVATION = Duration.ofMinutes(30);

    private final OrderRepository orderRepository;
    private final GetterTransaction getterTransaction;

    @Override
    public boolean hasOrders(PromoClient client) {
        return !client.isAnonymous()
                && orderRepository.existsRetailOrderByCustomer(client.email(), client.phoneLast10(), client.deviceId());
    }

    @Override
    public boolean usedPopup(UUID popupId, PromoClient client) {
        return popupId != null && !client.isAnonymous()
                && getterTransaction.popupCodeUsedByCustomer(popupId, client.email(), client.phoneLast10(),
                client.deviceId());
    }

    @Override
    public boolean usedCode(String code, PromoClient client) {
        return code != null && !client.isAnonymous()
                && getterTransaction.promoUsedByCustomer(code, client.email(), client.phoneLast10(), client.deviceId());
    }

    @Override
    public boolean exhausted(PromoCode promo, PromoClient client) {
        if (promo.getMaxUses() == null) {
            return false;
        }
        long uses = getterTransaction.countPromoUsesExceptCustomerPending(promo.getCode(), client.email(),
                client.phoneLast10(), client.deviceId(), Instant.now().minus(PENDING_RESERVATION));
        return uses >= promo.getMaxUses();
    }

    @Override
    public Optional<String> checkoutRejection(PromoCode promo, PromoClient client, String shopSlug) {
        String shop = shopSlug == null || shopSlug.isBlank() ? Shop.DEFAULT_SLUG : shopSlug.trim();
        String code = promo.getCode();
        if (!promo.allowedInShop(shop)) {
            return Optional.of("Промокод " + code + " действует только в другом магазине.");
        }
        if (!promo.belongsTo(client.email(), client.phoneLast10())) {
            return Optional.of("Промокод " + code + " персональный: укажите телефон или почту, на которые он выдан.");
        }
        if (usedCode(code, client)) {
            return Optional.of("Промокод " + code + " уже был использован.");
        }
        if (exhausted(promo, client)) {
            return Optional.of("Промокод " + code + " уже использован.");
        }
        if (promo.getPopupId() != null && usedPopup(promo.getPopupId(), client)) {
            return Optional.of("Скидка по этой акции уже использована.");
        }
        if (promo.isFirstOrderOnly() && hasOrders(client)) {
            return Optional.of("Промокод " + code + " действует только на первый заказ.");
        }
        return Optional.empty();
    }
}
