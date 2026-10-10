package ru.anyforms.service.promo.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupType;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.promo.PromoClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromoClientCheckerImplTest {

    private static final String DEVICE = "6f1c7a52-8b1e-4c39-9d7e-0a2b3c4d5e6f";

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final GetterPromoPopup getterPromoPopup = mock(GetterPromoPopup.class);
    private final PromoClientCheckerImpl checker = new PromoClientCheckerImpl(orderRepository, getterTransaction,
            getterPromoPopup);

    private final PromoClient client = PromoClient.of(" Buyer@Example.com ", "+7 (999) 123-45-67", DEVICE);

    @Test
    void normalizesClientIdentifiers() {
        assertEquals("buyer@example.com", client.email());
        assertEquals("9991234567", client.phoneLast10());
        assertEquals(DEVICE, client.deviceId());
        assertEquals("", PromoClient.of(null, null, "<script>").deviceId());
        assertTrue(PromoClient.of(null, null, null).isAnonymous());
    }

    @Test
    void anonymousClientIsNeverLookedUp() {
        PromoClient anonymous = PromoClient.of(null, null, null);

        assertFalse(checker.hasOrders(anonymous));
        assertFalse(checker.usedPopup(UUID.randomUUID(), anonymous));
        assertFalse(checker.usedCode("ANY-10", anonymous));
        verify(orderRepository, never()).existsRetailOrderByCustomer(anyString(), anyString(), anyString());
    }

    @Test
    void ordersAreSearchedByPhoneEmailAndDevice() {
        when(orderRepository.existsRetailOrderByCustomer("buyer@example.com", "9991234567", DEVICE)).thenReturn(true);

        assertTrue(checker.hasOrders(client));
    }

    @Test
    void singleUseCodeIsExhaustedAfterOneUse() {
        PromoCode promo = PromoCode.builder().code("ONE-AAAAA").maxUses(1).build();
        when(getterTransaction.countPromoUses(eq("ONE-AAAAA"), any())).thenReturn(1L);

        assertTrue(checker.exhausted(promo));
        assertFalse(checker.exhausted(PromoCode.builder().code("ANY-10").build()));
        verify(getterTransaction, never()).countPromoUses(eq("ANY-10"), any());
    }

    @Test
    void checkoutRejectsOtherShop() {
        PromoCode promo = PromoCode.builder().code("ONE-AAAAA").shopSlug("anyforms").build();

        assertTrue(checker.checkoutRejection(promo, client, "af_pastry").orElseThrow().contains("другом магазине"));
        assertTrue(checker.checkoutRejection(promo, client, null).isEmpty());
    }

    @Test
    void checkoutRejectsPersonalCodeOfAnotherClient() {
        PromoCode promo = PromoCode.builder().code("SHOP-AAAAA").ownerEmail("other@example.com")
                .ownerPhoneLast10("9210000000").build();

        Optional<String> rejection = checker.checkoutRejection(promo, client, "anyforms");

        assertTrue(rejection.orElseThrow().contains("персональный"));
    }

    @Test
    void previewIgnoresTheDevicesOwnPendingLinkButCheckoutCountsIt() {
        PromoCode promo = PromoCode.builder().code("ONE-AAAAA").maxUses(1).ownerDeviceId(DEVICE).build();
        when(getterTransaction.countPromoUses(eq("ONE-AAAAA"), any())).thenReturn(1L);
        when(getterTransaction.countPromoUsesExceptDevicePending(eq("ONE-AAAAA"), any(), eq(DEVICE))).thenReturn(0L);

        assertTrue(checker.previewRejection(promo, client, "anyforms").isEmpty());
        assertTrue(checker.checkoutRejection(promo, client, "anyforms").orElseThrow().contains("уже использован"));
    }

    @Test
    void checkoutRejectsDeviceCodeFromAnotherDevice() {
        PromoCode promo = PromoCode.builder().code("ONE-AAAAA").maxUses(1)
                .ownerDeviceId("0aa1bb2c-3dd4-4ee5-8ff6-112233445566").build();

        Optional<String> rejection = checker.checkoutRejection(promo, client, "anyforms");

        assertTrue(rejection.orElseThrow().contains("другое устройство"));
        assertTrue(checker.checkoutRejection(
                PromoCode.builder().code("ONE-BBBBB").maxUses(1).ownerDeviceId(DEVICE).build(), client, "anyforms").isEmpty());
    }

    @Test
    void checkoutRejectsCodeUsedByClientOrByOthers() {
        PromoCode promo = PromoCode.builder().code("ONE-AAAAA").maxUses(1).build();
        when(getterTransaction.promoUsedByCustomer("ONE-AAAAA", "buyer@example.com", "9991234567", DEVICE))
                .thenReturn(true);
        assertTrue(checker.checkoutRejection(promo, client, "anyforms").orElseThrow().contains("уже был использован"));

        when(getterTransaction.promoUsedByCustomer(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        when(getterTransaction.countPromoUses(eq("ONE-AAAAA"), any())).thenReturn(1L);
        assertTrue(checker.checkoutRejection(promo, client, "anyforms").orElseThrow().contains("уже использован"));
    }

    @Test
    void checkoutAllowsOnlyOneDiscountPerPopupAndFirstOrderOnly() {
        UUID popupId = UUID.randomUUID();
        PromoCode promo = PromoCode.builder().code("ONE-AAAAA").popupId(popupId).maxUses(1).firstOrderOnly(true).build();

        when(getterTransaction.popupCodeUsedByCustomer(popupId, "buyer@example.com", "9991234567", DEVICE))
                .thenReturn(true);
        assertEquals("Скидка по этой акции уже использована.",
                checker.checkoutRejection(promo, client, "anyforms").orElseThrow());

        when(getterTransaction.popupCodeUsedByCustomer(any(), anyString(), anyString(), anyString())).thenReturn(false);
        when(orderRepository.existsRetailOrderByCustomer("buyer@example.com", "9991234567", DEVICE)).thenReturn(true);
        assertTrue(checker.checkoutRejection(promo, client, "anyforms").orElseThrow().contains("первый заказ"));

        when(orderRepository.existsRetailOrderByCustomer(anyString(), anyString(), anyString())).thenReturn(false);
        assertTrue(checker.checkoutRejection(promo, client, "anyforms").isEmpty());
    }

    @Test
    void afterPurchaseCodesAreNotLimitedToOneDiscountPerClient() {
        UUID popupId = UUID.randomUUID();
        PromoCode promo = PromoCode.builder().code("NEXT-AAAAA").popupId(popupId).maxUses(1).build();
        when(getterPromoPopup.getById(popupId)).thenReturn(Optional.of(
                PromoPopup.builder().id(popupId).popupType(PromoPopupType.AFTER_PURCHASE).build()));
        when(getterTransaction.popupCodeUsedByCustomer(popupId, "buyer@example.com", "9991234567", DEVICE))
                .thenReturn(true);

        assertTrue(checker.checkoutRejection(promo, client, "anyforms").isEmpty());
        verify(getterTransaction, never()).popupCodeUsedByCustomer(any(), anyString(), anyString(), anyString());
    }
}
