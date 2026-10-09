package ru.anyforms.service.promo.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.promo.PromoPopupCreateUpdateRequest;
import ru.anyforms.dto.promo.PromoPopupDTO;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupType;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterPromoPopupView;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.PopupViewStats;
import ru.anyforms.repository.PromoPopupDeleter;
import ru.anyforms.repository.SaverPromoPopup;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromoPopupAdminServiceImplTest {

    private final GetterPromoPopup getterPromoPopup = mock(GetterPromoPopup.class);
    private final SaverPromoPopup saverPromoPopup = mock(SaverPromoPopup.class);
    private final PromoPopupDeleter promoPopupDeleter = mock(PromoPopupDeleter.class);
    private final GetterPromoPopupLead getterPromoPopupLead = mock(GetterPromoPopupLead.class);
    private final GetterPromoCode getterPromoCode = mock(GetterPromoCode.class);
    private final GetterPromoPopupView getterPromoPopupView = mock(GetterPromoPopupView.class);
    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);

    private final PromoPopupAdminServiceImpl service = new PromoPopupAdminServiceImpl(
            getterPromoPopup, saverPromoPopup, promoPopupDeleter, getterPromoPopupLead, getterPromoCode,
            getterPromoPopupView, getterTransaction);

    @BeforeEach
    void setUp() {
        when(saverPromoPopup.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(getterPromoPopupLead.countByPopup()).thenReturn(Map.of());
    }

    private static PromoPopupCreateUpdateRequest.PromoPopupCreateUpdateRequestBuilder base(PromoPopupType type) {
        return PromoPopupCreateUpdateRequest.builder()
                .popupType(type)
                .name("Выходные −20%")
                .active(true)
                .title("скидка {discount} до {until}")
                .buttonText("Скопировать промокод")
                .delaySeconds(10)
                .repeatAfterHours(6)
                .maxShows(3);
    }

    @Test
    void publicCodePopupStoresPromoAndDropsContactSettings() {
        UUID promoId = UUID.randomUUID();
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(
                PromoCode.builder().id(promoId).code("WEEKEND20").discountPercent(20).active(true).build()));

        PromoPopupDTO dto = service.create(base(PromoPopupType.PUBLIC_CODE)
                .promoCodeId(promoId)
                .discountPercent(15)
                .codePrefix("SHOP")
                .codeTtlDays(14)
                .amoResponsibleUserId(1L)
                .amoTaskTypeId(1L)
                .build());

        ArgumentCaptor<PromoPopup> saved = ArgumentCaptor.forClass(PromoPopup.class);
        verify(saverPromoPopup).save(saved.capture());
        PromoPopup popup = saved.getValue();
        assertEquals(PromoPopupType.PUBLIC_CODE, popup.getPopupType());
        assertEquals(promoId, popup.getPromoCodeId());
        assertEquals(6, popup.getRepeatAfterHours());
        assertEquals(3, popup.getMaxShows());
        assertNull(popup.getDiscountPercent());
        assertNull(popup.getCodePrefix());
        assertNull(popup.getAmoResponsibleUserId());
        assertFalse(popup.getFirstOrderOnly());
        assertEquals("WEEKEND20", dto.promo().code());
        assertTrue(dto.promo().currentlyValid());
        assertFalse(popup.getHideForKnownContacts());
    }

    @Test
    void uniqueCodePopupNeedsCodeSettingsButNotAmo() {
        service.create(base(PromoPopupType.UNIQUE_CODE)
                .successTitle("ваш код")
                .discountPercent(10)
                .codePrefix("one")
                .codeTtlDays(7)
                .amoResponsibleUserId(1L)
                .amoTaskTypeId(1L)
                .build());

        ArgumentCaptor<PromoPopup> saved = ArgumentCaptor.forClass(PromoPopup.class);
        verify(saverPromoPopup).save(saved.capture());
        PromoPopup popup = saved.getValue();
        assertEquals(PromoPopupType.UNIQUE_CODE, popup.getPopupType());
        assertEquals("ONE", popup.getCodePrefix());
        assertNull(popup.getSuccessTitle());
        assertNull(popup.getAmoResponsibleUserId());
        assertNull(popup.getAmoTaskTypeId());
        assertFalse(popup.getFirstOrderOnly());
        assertTrue(popup.getHideForKnownContacts());
    }

    @Test
    void generatedCodeIsForFirstOrderOnlyWhenAsked() {
        service.create(base(PromoPopupType.UNIQUE_CODE)
                .discountPercent(10)
                .codePrefix("ONE")
                .codeTtlDays(7)
                .firstOrderOnly(true)
                .build());

        ArgumentCaptor<PromoPopup> saved = ArgumentCaptor.forClass(PromoPopup.class);
        verify(saverPromoPopup).save(saved.capture());
        assertTrue(saved.getValue().getFirstOrderOnly());
    }

    @Test
    void afterPurchasePopupIgnoresFrequencyAndFirstOrderSettings() {
        service.create(base(PromoPopupType.AFTER_PURCHASE)
                .discountPercent(10)
                .codePrefix("next")
                .codeTtlDays(30)
                .firstOrderOnly(true)
                .hideForKnownContacts(true)
                .build());

        ArgumentCaptor<PromoPopup> saved = ArgumentCaptor.forClass(PromoPopup.class);
        verify(saverPromoPopup).save(saved.capture());
        PromoPopup popup = saved.getValue();
        assertEquals(PromoPopupType.AFTER_PURCHASE, popup.getPopupType());
        assertEquals("NEXT", popup.getCodePrefix());
        assertEquals(30, popup.getCodeTtlDays());
        assertFalse(popup.getFirstOrderOnly());
        assertFalse(popup.getHideForKnownContacts());
        assertEquals(0, popup.getDelaySeconds());
        assertEquals(0, popup.getRepeatAfterHours());
        assertNull(popup.getMaxShows());
        assertNull(popup.getAmoResponsibleUserId());
    }

    @Test
    void afterPurchasePopupRequiresCodeSettings() {
        assertThrows(ResponseStatusException.class, () -> service.create(base(PromoPopupType.AFTER_PURCHASE).build()));
        verify(saverPromoPopup, never()).save(any());
    }

    @Test
    void uniqueCodePopupDoesNotNeedSuccessScreen() {
        service.create(base(PromoPopupType.UNIQUE_CODE)
                .discountPercent(10)
                .codePrefix("ONE")
                .codeTtlDays(7)
                .build());

        verify(saverPromoPopup).save(any());
    }

    @Test
    void uniqueCodePopupRequiresDiscountSettings() {
        assertThrows(ResponseStatusException.class, () -> service.create(base(PromoPopupType.UNIQUE_CODE)
                .successTitle("ваш код")
                .build()));
        verify(saverPromoPopup, never()).save(any());
    }

    @Test
    void listShowsViewsIssuedAndPaidCounts() {
        UUID id = UUID.randomUUID();
        when(getterPromoPopup.getAll()).thenReturn(List.of(PromoPopup.builder().id(id).name("Попап")
                .popupType(PromoPopupType.UNIQUE_CODE).build()));
        when(getterPromoPopupLead.countByPopup()).thenReturn(Map.of(id, 4L));
        when(getterPromoPopupView.statsByPopup()).thenReturn(Map.of(id, new PopupViewStats(30, 12)));
        when(getterTransaction.countSucceededByPopup()).thenReturn(Map.of(id, 2L));

        PromoPopupDTO dto = service.list().get(0);

        assertEquals(4L, dto.leadsCount());
        assertEquals(30L, dto.viewsCount());
        assertEquals(12L, dto.viewDevicesCount());
        assertEquals(2L, dto.usedCount());
    }

    @Test
    void publicCodePopupRequiresPromo() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.create(base(PromoPopupType.PUBLIC_CODE).build()));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(saverPromoPopup, never()).save(any());
    }

    @Test
    void publicCodePopupRejectsPersonalPromo() {
        UUID promoId = UUID.randomUUID();
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(PromoCode.builder().id(promoId)
                .code("SHOP-AAAAA").discountPercent(15).active(true).ownerEmail("a@b.ru").build()));

        assertThrows(ResponseStatusException.class,
                () -> service.create(base(PromoPopupType.PUBLIC_CODE).promoCodeId(promoId).build()));
        verify(saverPromoPopup, never()).save(any());
    }

    @Test
    void contactPopupRequiresAmoSettings() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.create(base(PromoPopupType.CONTACT)
                        .successTitle("ваш промокод готов")
                        .discountPercent(15)
                        .codePrefix("SHOP")
                        .codeTtlDays(14)
                        .build()));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void contactPopupKeepsItsSettings() {
        service.create(base(PromoPopupType.CONTACT)
                .successTitle("ваш промокод готов")
                .discountPercent(15)
                .codePrefix("shop")
                .codeTtlDays(14)
                .amoResponsibleUserId(12675018L)
                .amoTaskTypeId(1L)
                .build());

        ArgumentCaptor<PromoPopup> saved = ArgumentCaptor.forClass(PromoPopup.class);
        verify(saverPromoPopup).save(saved.capture());
        PromoPopup popup = saved.getValue();
        assertEquals("SHOP", popup.getCodePrefix());
        assertFalse(popup.getFirstOrderOnly());
        assertEquals(60, popup.getAmoTaskDeadlineMinutes());
        assertNull(popup.getPromoCodeId());
    }

    @Test
    void typeCannotChangeAfterCodesWereIssued() {
        UUID id = UUID.randomUUID();
        when(getterPromoPopup.getById(id)).thenReturn(Optional.of(
                PromoPopup.builder().id(id).popupType(PromoPopupType.CONTACT).build()));
        when(getterPromoPopupLead.countByPopup()).thenReturn(Map.of(id, 2L));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.update(id, base(PromoPopupType.PUBLIC_CODE).promoCodeId(UUID.randomUUID()).build()));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }
}
