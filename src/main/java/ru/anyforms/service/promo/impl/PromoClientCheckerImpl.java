package ru.anyforms.service.promo.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.anyforms.model.marketplace.Shop;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.promo.PromoClient;
import ru.anyforms.service.promo.PromoClientChecker;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PromoClientCheckerImpl implements PromoClientChecker {

    private final OrderRepository orderRepository;
    private final GetterTransaction getterTransaction;
    private final GetterPromoPopup getterPromoPopup;

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
    public boolean exhausted(PromoCode promo) {
        if (promo.getMaxUses() == null) {
            return false;
        }
        return getterTransaction.countPromoUses(promo.getCode(), Instant.now().minus(PENDING_RESERVATION)) >= promo.getMaxUses();
    }

    private boolean oneDiscountPerClient(UUID popupId) {
        return getterPromoPopup.getById(popupId).map(PromoPopup::isAfterPurchase).map(after -> !after).orElse(true);
    }

    @Override
    public Optional<String> checkoutRejection(PromoCode promo, PromoClient client, String shopSlug) {
        String shop = shopSlug == null || shopSlug.isBlank() ? Shop.DEFAULT_SLUG : shopSlug.trim();
        String code = promo.getCode();
        if (!promo.allowedInShop(shop)) {
            return Optional.of("Промокод " + code + " действует только в другом магазине.");
        }
        if (!promo.belongsTo(client.email(), client.phoneLast10(), client.deviceId())) {
            return Optional.of(promo.hasContactOwner()
                    ? "Промокод " + code + " персональный: укажите телефон или почту, на которые он выдан."
                    : "Промокод " + code + " выдан на другое устройство: откройте магазин в том браузере, где получили код.");
        }
        if (usedCode(code, client)) {
            return Optional.of("Промокод " + code + " уже был использован.");
        }
        if (exhausted(promo)) {
            return Optional.of("Промокод " + code + " уже использован.");
        }
        if (promo.getPopupId() != null && oneDiscountPerClient(promo.getPopupId())
                && usedPopup(promo.getPopupId(), client)) {
            return Optional.of("Скидка по этой акции уже использована.");
        }
        if (promo.isFirstOrderOnly() && hasOrders(client)) {
            return Optional.of("Промокод " + code + " действует только на первый заказ.");
        }
        return Optional.empty();
    }
}
