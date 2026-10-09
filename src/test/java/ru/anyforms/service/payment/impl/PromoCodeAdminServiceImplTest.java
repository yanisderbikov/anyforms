package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.PromoCodeDTO;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.PromoCodeDeleter;
import ru.anyforms.repository.SaverPromoCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromoCodeAdminServiceImplTest {

    private final GetterPromoCode getterPromoCode = mock(GetterPromoCode.class);
    private final SaverPromoCode saverPromoCode = mock(SaverPromoCode.class);
    private final PromoCodeDeleter promoCodeDeleter = mock(PromoCodeDeleter.class);
    private final GetterPromoPopup getterPromoPopup = mock(GetterPromoPopup.class);
    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);

    private final PromoCodeAdminServiceImpl service = new PromoCodeAdminServiceImpl(
            getterPromoCode, saverPromoCode, promoCodeDeleter, getterPromoPopup, getterTransaction);

    @Test
    void listCountsUsesWithOneGroupedQuery() {
        PromoCode used = PromoCode.builder().id(UUID.randomUUID()).code("WEEKEND20").build();
        PromoCode fresh = PromoCode.builder().id(UUID.randomUUID()).code("ANY-10").build();
        when(getterPromoCode.getAllNotExpired(any())).thenReturn(List.of(used, fresh));
        when(getterTransaction.countSucceededByPromoCodes(List.of("WEEKEND20", "ANY-10"))).thenReturn(Map.of("WEEKEND20", 3L));

        List<PromoCodeDTO> list = service.listNotExpired();

        assertEquals(3L, list.get(0).usesCount());
        assertEquals(0L, list.get(1).usesCount());
        verify(getterTransaction, never()).countSucceededByPromoCode(any());
    }

    @Test
    void refusesToDeleteCodeShownInPopup() {
        UUID id = UUID.randomUUID();
        when(getterPromoCode.getById(id)).thenReturn(Optional.of(PromoCode.builder().id(id).code("WEEKEND20").build()));
        when(getterPromoPopup.getByPromoCodeId(id)).thenReturn(List.of(PromoPopup.builder().name("Выходные").build()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.delete(id));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().contains("«Выходные»"));
        verify(promoCodeDeleter, never()).deleteById(any());
    }

    @Test
    void deletesCodeNotUsedByPopups() {
        UUID id = UUID.randomUUID();
        when(getterPromoCode.getById(id)).thenReturn(Optional.of(PromoCode.builder().id(id).code("ANY-10").build()));
        when(getterPromoPopup.getByPromoCodeId(id)).thenReturn(List.of());

        service.delete(id);

        verify(promoCodeDeleter).deleteById(id);
    }
}
