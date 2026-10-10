package ru.anyforms.service.promo.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.amo.PromoPopupAmoLeadTaskPayload;
import ru.anyforms.dto.email.PromoPopupCodeEmailPayload;
import ru.anyforms.dto.promo.AfterPurchasePromoOutcome;
import ru.anyforms.dto.promo.PromoPopupActiveRequest;
import ru.anyforms.dto.promo.PromoPopupClaimRequest;
import ru.anyforms.dto.promo.PromoPopupAfterPurchaseRequest;
import ru.anyforms.dto.promo.PromoPopupClaimResponse;
import ru.anyforms.dto.promo.PromoPopupIssueRequest;
import ru.anyforms.dto.promo.PublicPromoPopupDTO;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.OrderSource;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.model.promo.PromoPopupType;
import ru.anyforms.model.promo.PromoPopupView;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterPromoPopupView;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverPromoCode;
import ru.anyforms.repository.SaverPromoPopupLead;
import ru.anyforms.repository.SaverPromoPopupView;
import ru.anyforms.repository.TransactionLock;
import ru.anyforms.service.promo.PromoClient;
import ru.anyforms.service.promo.PromoClientChecker;
import ru.anyforms.service.task.TaskAdder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromoPopupPublicServiceImplTest {

    private static final String CONSENT_VERSION = "2026-10-05";
    private static final String DEVICE = "6f1c7a52-8b1e-4c39-9d7e-0a2b3c4d5e6f";
    private static final String OTHER_DEVICE = "0aa1bb2c-3dd4-4ee5-8ff6-112233445566";
    private static final UUID POPUP_ID = UUID.randomUUID();
    private static final UUID UNIQUE_ID = UUID.randomUUID();
    private static final UUID AFTER_ID = UUID.randomUUID();

    private final GetterPromoPopup getterPromoPopup = mock(GetterPromoPopup.class);
    private final GetterPromoPopupLead getterPromoPopupLead = mock(GetterPromoPopupLead.class);
    private final SaverPromoPopupLead saverPromoPopupLead = mock(SaverPromoPopupLead.class);
    private final GetterPromoPopupView getterPromoPopupView = mock(GetterPromoPopupView.class);
    private final SaverPromoPopupView saverPromoPopupView = mock(SaverPromoPopupView.class);
    private final GetterPromoCode getterPromoCode = mock(GetterPromoCode.class);
    private final SaverPromoCode saverPromoCode = mock(SaverPromoCode.class);
    private final PromoClientChecker checker = mock(PromoClientChecker.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final TaskAdder taskAdder = mock(TaskAdder.class);
    private final ClaimRateLimiter claimRateLimiter = mock(ClaimRateLimiter.class);
    private final TransactionLock transactionLock = mock(TransactionLock.class);

    private final PromoPopupPublicServiceImpl service = new PromoPopupPublicServiceImpl(
            getterPromoPopup, getterPromoPopupLead, saverPromoPopupLead, getterPromoPopupView, saverPromoPopupView,
            getterPromoCode, saverPromoCode, checker, orderRepository, taskAdder, claimRateLimiter, transactionLock);

    private PromoPopup contactPopup;
    private PromoPopup uniquePopup;
    private PromoPopup afterPurchasePopup;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "consentVersion", CONSENT_VERSION);
        contactPopup = PromoPopup.builder()
                .id(POPUP_ID)
                .name("Скидка 15%")
                .popupType(PromoPopupType.CONTACT)
                .active(true)
                .priority(0)
                .shopSlug("anyforms")
                .discountPercent(15)
                .codePrefix("SHOP")
                .codeTtlDays(14)
                .firstOrderOnly(true)
                .hideForKnownContacts(true)
                .repeatAfterHours(24)
                .maxShows(3)
                .build();
        uniquePopup = PromoPopup.builder()
                .id(UNIQUE_ID)
                .name("Одноразовый −10%")
                .popupType(PromoPopupType.UNIQUE_CODE)
                .active(true)
                .priority(0)
                .shopSlug("anyforms")
                .discountPercent(10)
                .codePrefix("ONE")
                .codeTtlDays(7)
                .firstOrderOnly(true)
                .hideForKnownContacts(true)
                .repeatAfterHours(0)
                .build();
        afterPurchasePopup = PromoPopup.builder()
                .id(AFTER_ID)
                .name("−10% на следующий заказ")
                .popupType(PromoPopupType.AFTER_PURCHASE)
                .active(true)
                .priority(100)
                .shopSlug("anyforms")
                .title("спасибо за заказ")
                .buttonText("Вернуться в магазин")
                .discountPercent(10)
                .codePrefix("NEXT")
                .codeTtlDays(30)
                .firstOrderOnly(false)
                .hideForKnownContacts(false)
                .repeatAfterHours(0)
                .build();
        when(getterPromoPopup.getById(POPUP_ID)).thenReturn(Optional.of(contactPopup));
        when(getterPromoPopup.getById(UNIQUE_ID)).thenReturn(Optional.of(uniquePopup));
        when(getterPromoPopup.getById(AFTER_ID)).thenReturn(Optional.of(afterPurchasePopup));
        when(getterPromoPopupLead.getLatestForOrder(any(), any())).thenReturn(Optional.empty());
        when(claimRateLimiter.tryAcquire(any(), anyInt())).thenReturn(true);
        when(getterPromoPopupLead.getLatestForClient(any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(getterPromoPopupView.lastViewAt(any(), anyString())).thenReturn(Optional.empty());
        when(getterPromoCode.getByCode(anyString())).thenReturn(Optional.empty());
        when(saverPromoCode.save(any())).thenAnswer(inv -> {
            PromoCode promo = inv.getArgument(0);
            promo.setId(UUID.randomUUID());
            return promo;
        });
        when(saverPromoPopupLead.save(any())).thenAnswer(inv -> {
            PromoPopupLead lead = inv.getArgument(0);
            lead.setId(UUID.randomUUID());
            return lead;
        });
    }

    private static PromoPopupClaimRequest claimRequest() {
        return PromoPopupClaimRequest.builder()
                .email(" Buyer@Example.com ")
                .phone("8 (999) 123-45-67")
                .consentPersonalData(true)
                .consentAdvertising(true)
                .consentVersion(CONSENT_VERSION)
                .deviceId(DEVICE)
                .utmSource("telegram")
                .build();
    }

    private static PromoPopupIssueRequest issueRequest() {
        return PromoPopupIssueRequest.builder().deviceId(DEVICE).pageUrl("https://anyforms.ru/shop").build();
    }

    private static PromoPopupAfterPurchaseRequest afterPurchaseRequest() {
        return PromoPopupAfterPurchaseRequest.builder().orderNumber(" a1b2c3 ").deviceId(DEVICE).build();
    }

    private Order paidOrder(OrderPaymentStatus status) {
        Order order = new Order();
        order.setId(42L);
        order.setPublicId("A1B2C3");
        order.setSource(OrderSource.MARKETPLACE);
        order.setRetail(true);
        order.setPaymentStatus(status);
        order.setEmail("Buyer@Example.com");
        order.setContactPhone("+7 (999) 123-45-67");
        order.setDeviceId(OTHER_DEVICE);
        when(orderRepository.findByPublicId("A1B2C3")).thenReturn(Optional.of(order));
        return order;
    }

    private static PromoPopupActiveRequest activeRequest(String phone) {
        return PromoPopupActiveRequest.builder().shop("anyforms").deviceId(DEVICE).phone(phone).build();
    }

    private PromoCode capturedPromo() {
        ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
        verify(saverPromoCode).save(captor.capture());
        return captor.getValue();
    }

    private PromoPopupLead capturedLead() {
        ArgumentCaptor<PromoPopupLead> captor = ArgumentCaptor.forClass(PromoPopupLead.class);
        verify(saverPromoPopupLead).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void claimIssuesSingleUseCodeBoundToContactAndDevice() {
        PromoPopupClaimResponse response = service.claim(POPUP_ID, claimRequest(), "1.2.3.4", "UA");

        verify(transactionLock).lockAll(List.of(
                "promo-popup:" + POPUP_ID + ":email:buyer@example.com",
                "promo-popup:" + POPUP_ID + ":phone:9991234567",
                "promo-popup:" + POPUP_ID + ":device:" + DEVICE));
        PromoCode promo = capturedPromo();
        assertTrue(promo.getCode().matches("SHOP-[" + PromoPopupPublicServiceImpl.CODE_ALPHABET + "]{5}"));
        assertEquals(1, promo.getMaxUses());
        assertEquals("buyer@example.com", promo.getOwnerEmail());
        assertEquals("9991234567", promo.getOwnerPhoneLast10());
        assertEquals(DEVICE, promo.getOwnerDeviceId());
        assertEquals("anyforms", promo.getShopSlug());
        assertTrue(promo.isFirstOrderOnly());
        assertEquals(Duration.ofDays(14), Duration.between(promo.getValidFrom(), promo.getValidUntil()));

        PromoPopupLead lead = capturedLead();
        assertEquals("+79991234567", lead.getPhone());
        assertEquals(DEVICE, lead.getDeviceId());
        assertEquals(CONSENT_VERSION, lead.getConsentVersion());
        assertEquals("telegram", lead.getUtmSource());

        ArgumentCaptor<Object> tasks = ArgumentCaptor.forClass(Object.class);
        verify(taskAdder, times(2)).addTask(tasks.capture());
        assertTrue(tasks.getAllValues().get(0) instanceof PromoPopupAmoLeadTaskPayload);
        assertTrue(tasks.getAllValues().get(1) instanceof PromoPopupCodeEmailPayload);
        assertEquals(promo.getCode(), response.code());
        assertFalse(response.repeated());
    }

    @Test
    void claimChecksClientByPhoneEmailAndDevice() {
        when(checker.hasOrders(new PromoClient("buyer@example.com", "9991234567", DEVICE))).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, claimRequest(), "1.2.3.4", "UA"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(saverPromoCode, never()).save(any());
        verify(taskAdder, never()).addTask(any());
    }

    @Test
    void claimRefusesWhenPopupDiscountAlreadyUsed() {
        when(checker.usedPopup(eq(POPUP_ID), any())).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, claimRequest(), "1.2.3.4", "UA"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void claimReturnsSameCodeForSameContact() {
        UUID promoId = UUID.randomUUID();
        when(getterPromoPopupLead.getLatestForClient(POPUP_ID, "buyer@example.com", "9991234567", DEVICE))
                .thenReturn(Optional.of(PromoPopupLead.builder().popupId(POPUP_ID).promoCodeId(promoId)
                        .email("buyer@example.com").phoneLast10("9991234567").code("SHOP-AAAAA").build()));
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(PromoCode.builder().id(promoId)
                .code("SHOP-AAAAA").discountPercent(15).active(true).maxUses(1)
                .validUntil(Instant.now().plus(Duration.ofDays(3))).build()));

        PromoPopupClaimResponse response = service.claim(POPUP_ID, claimRequest(), "1.2.3.4", "UA");

        assertEquals("SHOP-AAAAA", response.code());
        assertTrue(response.repeated());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void claimRefusesSecondContactFromSameDevice() {
        when(getterPromoPopupLead.getLatestForClient(POPUP_ID, "buyer@example.com", "9991234567", DEVICE))
                .thenReturn(Optional.of(PromoPopupLead.builder().popupId(POPUP_ID).deviceId(DEVICE)
                        .email("other@example.com").phoneLast10("9210000000").code("SHOP-BBBBB").build()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, claimRequest(), "1.2.3.4", "UA"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().contains("устройства"));
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void claimRefusesWhenEarlierCodeAlreadyUsed() {
        UUID promoId = UUID.randomUUID();
        when(getterPromoPopupLead.getLatestForClient(any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(PromoPopupLead.builder().popupId(POPUP_ID).promoCodeId(promoId)
                        .email("buyer@example.com").code("SHOP-AAAAA").build()));
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(PromoCode.builder().id(promoId)
                .code("SHOP-AAAAA").discountPercent(15).active(true).maxUses(1).build()));
        when(checker.usedCode(eq("SHOP-AAAAA"), any())).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, claimRequest(), "1.2.3.4", "UA"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void claimRejectsFinishedPopupBotsLimitsConsentAndPhone() {
        PromoPopupClaimRequest honeypot = claimRequest();
        honeypot.setWebsite("http://spam");
        assertThrows(ResponseStatusException.class, () -> service.claim(POPUP_ID, honeypot, "1.2.3.4", "UA"));

        PromoPopupClaimRequest outdated = claimRequest();
        outdated.setConsentVersion("2020-01-01");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, outdated, "1.2.3.4", "UA")).getStatusCode());

        PromoPopupClaimRequest badPhone = claimRequest();
        badPhone.setPhone("12345");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, badPhone, "1.2.3.4", "UA")).getStatusCode());

        when(claimRateLimiter.tryAcquire(eq("claim:5.6.7.8"), anyInt())).thenReturn(false);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, claimRequest(), "5.6.7.8", "UA")).getStatusCode());

        contactPopup.setValidUntil(Instant.now().minusSeconds(1));
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> service.claim(POPUP_ID, claimRequest(), "1.2.3.4", "UA")).getStatusCode());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void claimDoesNotWorkForUniqueCodePopup() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.claim(UNIQUE_ID, claimRequest(), "1.2.3.4", "UA")).getStatusCode());
    }

    @Test
    void issueGeneratesSingleUseCodeForDeviceWithoutContacts() {
        PromoPopupClaimResponse response = service.issue(UNIQUE_ID, issueRequest(), "1.2.3.4", "UA");

        verify(transactionLock).lock("promo-popup:" + UNIQUE_ID + ":device:" + DEVICE);
        PromoCode promo = capturedPromo();
        assertTrue(promo.getCode().startsWith("ONE-"));
        assertEquals(1, promo.getMaxUses());
        assertEquals(DEVICE, promo.getOwnerDeviceId());
        assertNull(promo.getOwnerEmail());
        assertNull(promo.getOwnerPhoneLast10());
        assertTrue(promo.isPersonal());
        assertFalse(promo.hasContactOwner());
        assertEquals(Duration.ofDays(7), Duration.between(promo.getValidFrom(), promo.getValidUntil()));

        PromoPopupLead lead = capturedLead();
        assertEquals(DEVICE, lead.getDeviceId());
        assertNull(lead.getEmail());
        assertNull(lead.getConsentVersion());
        verify(taskAdder, never()).addTask(any());
        assertFalse(response.repeated());
    }

    @Test
    void issueReturnsSameCodeToSameDevice() {
        UUID promoId = UUID.randomUUID();
        when(getterPromoPopupLead.getLatestForClient(UNIQUE_ID, "", "", DEVICE))
                .thenReturn(Optional.of(PromoPopupLead.builder().popupId(UNIQUE_ID).promoCodeId(promoId)
                        .deviceId(DEVICE).code("ONE-CCCCC").build()));
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(PromoCode.builder().id(promoId)
                .code("ONE-CCCCC").discountPercent(10).active(true).maxUses(1)
                .validUntil(Instant.now().plus(Duration.ofDays(2))).build()));

        PromoPopupClaimResponse response = service.issue(UNIQUE_ID, issueRequest(), "1.2.3.4", "UA");

        assertEquals("ONE-CCCCC", response.code());
        assertTrue(response.repeated());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void issueRefusesUsedOrExpiredCodeOfDevice() {
        UUID promoId = UUID.randomUUID();
        when(getterPromoPopupLead.getLatestForClient(UNIQUE_ID, "", "", DEVICE))
                .thenReturn(Optional.of(PromoPopupLead.builder().popupId(UNIQUE_ID).promoCodeId(promoId)
                        .deviceId(DEVICE).code("ONE-CCCCC").build()));
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(PromoCode.builder().id(promoId)
                .code("ONE-CCCCC").discountPercent(10).active(true).maxUses(1).build()));
        when(checker.exhausted(any())).thenReturn(true);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.issue(UNIQUE_ID, issueRequest(), "1.2.3.4", "UA")).getStatusCode());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void issueRequiresDeviceAndChecksKnownClient() {
        PromoPopupIssueRequest noDevice = PromoPopupIssueRequest.builder().deviceId("bad").build();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.issue(UNIQUE_ID, noDevice, "1.2.3.4", "UA")).getStatusCode());

        PromoPopupIssueRequest knownPhone = issueRequest();
        knownPhone.setPhone("+7 999 123-45-67");
        when(checker.hasOrders(new PromoClient("", "9991234567", DEVICE))).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.issue(UNIQUE_ID, knownPhone, "1.2.3.4", "UA")).getStatusCode());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void activeHidesPopupFromVisitorWhoEnteredPhone() {
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(contactPopup));

        assertTrue(service.getActive(activeRequest("+79991234567")).isEmpty());
        assertTrue(service.getActive(activeRequest(null)).isPresent());
    }

    @Test
    void activeShowsToKnownVisitorWhenAllowedButSkipsExistingCustomers() {
        contactPopup.setHideForKnownContacts(false);
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(contactPopup));

        assertTrue(service.getActive(activeRequest("+79991234567")).isPresent());

        when(checker.hasOrders(new PromoClient("", "9991234567", DEVICE))).thenReturn(true);
        assertTrue(service.getActive(activeRequest("+79991234567")).isEmpty());
    }

    @Test
    void activeRespectsShowsLimitAndIntervalPerDevice() {
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(contactPopup));

        when(getterPromoPopupView.countForDevice(POPUP_ID, DEVICE)).thenReturn(3L);
        assertTrue(service.getActive(activeRequest(null)).isEmpty());

        when(getterPromoPopupView.countForDevice(POPUP_ID, DEVICE)).thenReturn(1L);
        when(getterPromoPopupView.lastViewAt(POPUP_ID, DEVICE)).thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(2))));
        assertTrue(service.getActive(activeRequest(null)).isEmpty());

        when(getterPromoPopupView.lastViewAt(POPUP_ID, DEVICE)).thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(25))));
        assertTrue(service.getActive(activeRequest(null)).isPresent());
    }

    @Test
    void activeSkipsPopupWhenDeviceAlreadyGotCode() {
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(uniquePopup, contactPopup));
        when(getterPromoPopupLead.getLatestForClient(UNIQUE_ID, "", "", DEVICE))
                .thenReturn(Optional.of(PromoPopupLead.builder().popupId(UNIQUE_ID).deviceId(DEVICE).build()));

        PublicPromoPopupDTO dto = service.getActive(activeRequest(null)).orElseThrow();

        assertEquals(POPUP_ID, dto.id());
        assertEquals(PromoPopupType.CONTACT, dto.popupType());
    }

    @Test
    void activeShowsUniquePopupAgainWithStillUsableCode() {
        UUID promoId = UUID.randomUUID();
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(uniquePopup));
        when(getterPromoPopupLead.getLatestForClient(UNIQUE_ID, "", "", DEVICE))
                .thenReturn(Optional.of(PromoPopupLead.builder().popupId(UNIQUE_ID).promoCodeId(promoId)
                        .deviceId(DEVICE).code("ONE-CCCCC").build()));
        PromoCode promo = PromoCode.builder().id(promoId).code("ONE-CCCCC").discountPercent(10).active(true)
                .maxUses(1).validUntil(Instant.now().plus(Duration.ofDays(2))).build();
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(promo));

        assertEquals(UNIQUE_ID, service.getActive(activeRequest(null)).orElseThrow().id());

        when(checker.usedCode(eq("ONE-CCCCC"), any())).thenReturn(true);
        assertTrue(service.getActive(activeRequest(null)).isEmpty());

        when(checker.usedCode(eq("ONE-CCCCC"), any())).thenReturn(false);
        promo.setValidUntil(Instant.now().minusSeconds(1));
        assertTrue(service.getActive(activeRequest(null)).isEmpty());
    }

    @Test
    void issueRateLimitIsLooserThanClaim() {
        service.issue(UNIQUE_ID, issueRequest(), "1.2.3.4", "UA");

        verify(claimRateLimiter).tryAcquire("issue:1.2.3.4", ClaimRateLimiter.ISSUE_ATTEMPTS);
    }

    @Test
    void activeUniqueCodePopupDoesNotExposeCode() {
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(uniquePopup));

        PublicPromoPopupDTO dto = service.getActive(activeRequest(null)).orElseThrow();

        assertEquals(PromoPopupType.UNIQUE_CODE, dto.popupType());
        assertNull(dto.code());
        assertEquals(10, dto.discountPercent());
        assertEquals(7, dto.codeTtlDays());
    }

    @Test
    void activePublicCodePopupChecksPromoAndClient() {
        UUID promoId = UUID.randomUUID();
        Instant until = Instant.now().plus(Duration.ofDays(2));
        PromoPopup publicPopup = PromoPopup.builder().id(UUID.randomUUID()).popupType(PromoPopupType.PUBLIC_CODE)
                .active(true).shopSlug("anyforms").title("скидка {discount}").buttonText("Скопировать")
                .delaySeconds(10).repeatAfterHours(6).maxShows(3).promoCodeId(promoId)
                .hideForKnownContacts(false).build();
        PromoCode promo = PromoCode.builder().id(promoId).code("WEEKEND20").discountPercent(20).active(true)
                .validUntil(until).firstOrderOnly(true).build();
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(publicPopup));
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(promo));

        PublicPromoPopupDTO dto = service.getActive(activeRequest(null)).orElseThrow();
        assertEquals("WEEKEND20", dto.code());
        assertEquals(until.toString(), dto.codeValidUntil());

        when(checker.usedCode(eq("WEEKEND20"), any())).thenReturn(true);
        assertTrue(service.getActive(activeRequest(null)).isEmpty());

        when(checker.usedCode(eq("WEEKEND20"), any())).thenReturn(false);
        promo.setValidUntil(Instant.now().minusSeconds(60));
        assertTrue(service.getActive(activeRequest(null)).isEmpty());
    }

    @Test
    void recordViewSkipsRepeatsWithinWindowAndInvalidDevices() {
        service.recordView(POPUP_ID, DEVICE);
        verify(saverPromoPopupView, times(1)).save(any(PromoPopupView.class));

        when(getterPromoPopupView.lastViewAt(POPUP_ID, OTHER_DEVICE)).thenReturn(Optional.of(Instant.now().minusSeconds(30)));
        service.recordView(POPUP_ID, OTHER_DEVICE);
        service.recordView(POPUP_ID, "not a device");
        verify(saverPromoPopupView, times(1)).save(any(PromoPopupView.class));
    }

    @Test
    void afterPurchaseIssuesCodeBoundToOrderContactsAndDevice() {
        paidOrder(OrderPaymentStatus.PAID);
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(afterPurchasePopup, uniquePopup));

        AfterPurchasePromoOutcome outcome = service.afterPurchase(afterPurchaseRequest(), "1.2.3.4");

        assertEquals(AfterPurchasePromoOutcome.Status.READY, outcome.status());
        verify(transactionLock).lockAll(List.of("promo-popup:" + AFTER_ID + ":order:42"));
        PromoCode promo = capturedPromo();
        assertTrue(promo.getCode().startsWith("NEXT-"));
        assertEquals(1, promo.getMaxUses());
        assertEquals("buyer@example.com", promo.getOwnerEmail());
        assertEquals("9991234567", promo.getOwnerPhoneLast10());
        assertEquals(OTHER_DEVICE, promo.getOwnerDeviceId());
        assertFalse(promo.isFirstOrderOnly());
        assertEquals(Duration.ofDays(30), Duration.between(promo.getValidFrom(), promo.getValidUntil()));

        PromoPopupLead lead = capturedLead();
        assertEquals("+79991234567", lead.getPhone());
        assertEquals(42L, lead.getOrderId());
        assertEquals("buyer@example.com", lead.getEmail());
        assertEquals("9991234567", lead.getPhoneLast10());
        assertEquals(OTHER_DEVICE, lead.getDeviceId());
        assertNull(lead.getConsentVersion());
        verify(taskAdder, never()).addTask(any());
        assertEquals(promo.getCode(), outcome.promo().code());
        assertEquals("спасибо за заказ", outcome.promo().title());
        assertFalse(outcome.promo().repeated());
    }

    @Test
    void afterPurchaseNeverBindsTheCodeToTheCallersDevice() {
        Order order = paidOrder(OrderPaymentStatus.PAID);
        order.setDeviceId(null);
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(afterPurchasePopup));

        service.afterPurchase(afterPurchaseRequest(), "1.2.3.4");

        PromoCode promo = capturedPromo();
        assertNull(promo.getOwnerDeviceId());
        assertEquals("buyer@example.com", promo.getOwnerEmail());
        assertNull(capturedLead().getDeviceId());
    }

    @Test
    void afterPurchaseReturnsSameCodeForSameOrder() {
        paidOrder(OrderPaymentStatus.PAID);
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(afterPurchasePopup));
        UUID promoId = UUID.randomUUID();
        when(getterPromoPopupLead.getLatestForOrder(AFTER_ID, 42L)).thenReturn(Optional.of(
                PromoPopupLead.builder().popupId(AFTER_ID).promoCodeId(promoId).orderId(42L).code("NEXT-AAAAA").build()));
        PromoCode promo = PromoCode.builder().id(promoId).code("NEXT-AAAAA").discountPercent(10).active(true)
                .maxUses(1).validUntil(Instant.now().plus(Duration.ofDays(20))).build();
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(promo));

        AfterPurchasePromoOutcome outcome = service.afterPurchase(afterPurchaseRequest(), "1.2.3.4");

        assertEquals("NEXT-AAAAA", outcome.promo().code());
        assertTrue(outcome.promo().repeated());
        verify(saverPromoCode, never()).save(any());

        when(checker.usedCode(eq("NEXT-AAAAA"), any())).thenReturn(true);
        assertEquals(AfterPurchasePromoOutcome.Status.NONE, service.afterPurchase(afterPurchaseRequest(), "1.2.3.4").status());
    }

    @Test
    void afterPurchaseStopsIssuingWhenClientReachedLimit() {
        paidOrder(OrderPaymentStatus.PAID);
        afterPurchasePopup.setMaxShows(2);
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(afterPurchasePopup));
        when(getterPromoPopupLead.countForClient(AFTER_ID, "buyer@example.com", "9991234567", OTHER_DEVICE)).thenReturn(2L);

        assertEquals(AfterPurchasePromoOutcome.Status.NONE, service.afterPurchase(afterPurchaseRequest(), "1.2.3.4").status());
        verify(transactionLock).lockAll(List.of(
                "promo-popup:" + AFTER_ID + ":order:42",
                "promo-popup:" + AFTER_ID + ":email:buyer@example.com",
                "promo-popup:" + AFTER_ID + ":phone:9991234567",
                "promo-popup:" + AFTER_ID + ":device:" + OTHER_DEVICE));
        verify(saverPromoCode, never()).save(any());
        verify(saverPromoPopupLead, never()).save(any());

        when(getterPromoPopupLead.countForClient(AFTER_ID, "buyer@example.com", "9991234567", OTHER_DEVICE)).thenReturn(1L);
        assertEquals(AfterPurchasePromoOutcome.Status.READY, service.afterPurchase(afterPurchaseRequest(), "1.2.3.4").status());
        verify(saverPromoCode).save(any());
    }

    @Test
    void afterPurchaseKeepsIssuedCodeForSameOrderDespiteLimit() {
        paidOrder(OrderPaymentStatus.PAID);
        afterPurchasePopup.setMaxShows(1);
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(afterPurchasePopup));
        UUID promoId = UUID.randomUUID();
        when(getterPromoPopupLead.getLatestForOrder(AFTER_ID, 42L)).thenReturn(Optional.of(
                PromoPopupLead.builder().popupId(AFTER_ID).promoCodeId(promoId).orderId(42L).code("NEXT-BBBBB").build()));
        when(getterPromoCode.getById(promoId)).thenReturn(Optional.of(PromoCode.builder().id(promoId).code("NEXT-BBBBB")
                .discountPercent(10).active(true).maxUses(1).validUntil(Instant.now().plus(Duration.ofDays(20))).build()));

        AfterPurchasePromoOutcome outcome = service.afterPurchase(afterPurchaseRequest(), "1.2.3.4");

        assertEquals("NEXT-BBBBB", outcome.promo().code());
        verify(getterPromoPopupLead, never()).countForClient(any(), anyString(), anyString(), anyString());
    }

    @Test
    void afterPurchaseWaitsForPaymentAndSkipsUnpaidOrders() {
        paidOrder(OrderPaymentStatus.AWAITING_PAYMENT);
        assertEquals(AfterPurchasePromoOutcome.Status.AWAITING_PAYMENT,
                service.afterPurchase(afterPurchaseRequest(), "1.2.3.4").status());

        paidOrder(OrderPaymentStatus.CANCELED);
        assertEquals(AfterPurchasePromoOutcome.Status.NONE, service.afterPurchase(afterPurchaseRequest(), "1.2.3.4").status());

        paidOrder(OrderPaymentStatus.PAID);
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(uniquePopup, contactPopup));
        assertEquals(AfterPurchasePromoOutcome.Status.NONE, service.afterPurchase(afterPurchaseRequest(), "1.2.3.4").status());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void afterPurchaseRejectsUnknownOrderAndFloods() {
        PromoPopupAfterPurchaseRequest unknown = PromoPopupAfterPurchaseRequest.builder().orderNumber("ZZZZZZ").build();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.afterPurchase(unknown, "1.2.3.4")).getStatusCode());

        PromoPopupAfterPurchaseRequest malformed = PromoPopupAfterPurchaseRequest.builder().orderNumber("<b>").build();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.afterPurchase(malformed, "1.2.3.4")).getStatusCode());

        when(claimRateLimiter.tryAcquire(eq("after-purchase:5.6.7.8"), anyInt())).thenReturn(false);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.afterPurchase(afterPurchaseRequest(), "5.6.7.8")).getStatusCode());
    }

    @Test
    void activeNeverShowsAfterPurchasePopup() {
        when(getterPromoPopup.getLive(any(), any())).thenReturn(List.of(afterPurchasePopup, contactPopup));

        assertEquals(POPUP_ID, service.getActive(activeRequest(null)).orElseThrow().id());
    }
}
