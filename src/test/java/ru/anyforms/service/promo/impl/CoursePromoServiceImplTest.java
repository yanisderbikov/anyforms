package ru.anyforms.service.promo.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.dto.promo.CoursePromoOutcome;
import ru.anyforms.dto.promo.PromoPopupAfterPurchaseRequest;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.OrderSource;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.model.promo.PromoPopupType;
import ru.anyforms.repository.GetterPaymentProduct;
import ru.anyforms.repository.GetterPromoCode;
import ru.anyforms.repository.GetterPromoPopup;
import ru.anyforms.repository.GetterPromoPopupLead;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverPromoCode;
import ru.anyforms.repository.SaverPromoPopupLead;
import ru.anyforms.repository.TransactionLock;
import ru.anyforms.service.payment.PromoReservationService;
import ru.anyforms.service.promo.PromoClient;
import ru.anyforms.service.promo.PromoClientChecker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoursePromoServiceImplTest {

    private static final String ORDER_NUMBER = "A1B2C3";
    private static final long COURSE_PRICE = 1_690_000L;
    private static final UUID POPUP_ID = UUID.randomUUID();

    private final GetterPromoPopup getterPromoPopup = mock(GetterPromoPopup.class);
    private final GetterPromoPopupLead getterLead = mock(GetterPromoPopupLead.class);
    private final SaverPromoPopupLead saverLead = mock(SaverPromoPopupLead.class);
    private final GetterPromoCode getterPromoCode = mock(GetterPromoCode.class);
    private final SaverPromoCode saverPromoCode = mock(SaverPromoCode.class);
    private final GetterPaymentProduct getterPaymentProduct = mock(GetterPaymentProduct.class);
    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PromoClientChecker checker = mock(PromoClientChecker.class);
    private final PromoReservationService reservationService = mock(PromoReservationService.class);
    private final TransactionLock transactionLock = mock(TransactionLock.class);
    private final ClaimRateLimiter rateLimiter = mock(ClaimRateLimiter.class);

    private final CoursePromoServiceImpl service = new CoursePromoServiceImpl(getterPromoPopup, getterLead,
            saverLead, getterPromoCode, saverPromoCode, getterPaymentProduct, getterTransaction, orderRepository,
            checker, reservationService, transactionLock, rateLimiter, new PromoCodeGenerator(getterPromoCode));

    private final PaymentProduct course = PaymentProduct.builder()
            .code(PaymentProduct.CODE_COURSE_STREAM_2)
            .title("Курс по молдам")
            .priceKopecks(COURSE_PRICE)
            .active(true)
            .build();

    private final PromoPopup popup = PromoPopup.builder()
            .id(POPUP_ID)
            .name("Скидка на курс")
            .popupType(PromoPopupType.COURSE_AFTER_PURCHASE)
            .active(true)
            .shopSlug("anyforms")
            .title("скидка {discount} на курс")
            .buttonText("Открыть курс")
            .productCode(PaymentProduct.CODE_COURSE_STREAM_2)
            .codePrefix("COURSE")
            .codeTtlDays(90)
            .discountPercent(0)
            .build();

    private final Order order = new Order();

    @BeforeEach
    void setUp() {
        order.setId(42L);
        order.setPublicId(ORDER_NUMBER);
        order.setSource(OrderSource.MARKETPLACE);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setEmail("Buyer@Example.com");
        order.setContactPhone("+7 (999) 123-45-67");

        when(rateLimiter.tryAcquire(anyString(), eq(ClaimRateLimiter.ISSUE_ATTEMPTS))).thenReturn(true);
        when(orderRepository.findByPublicId(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(popup));
        when(getterPaymentProduct.getByCode(PaymentProduct.CODE_COURSE_STREAM_2)).thenReturn(Optional.of(course));
        when(getterLead.getLatestForOrder(POPUP_ID, 42L)).thenReturn(Optional.empty());
        when(getterPromoCode.getByCode(anyString())).thenReturn(Optional.empty());
        when(getterTransaction.getByOrderId(42L)).thenReturn(List.of(
                transaction(PaymentTransactionStatus.SUCCEEDED, 500_000L),
                transaction(PaymentTransactionStatus.CANCELED, 500_000L)));
        when(saverPromoCode.save(any())).thenAnswer(inv -> {
            PromoCode promo = inv.getArgument(0);
            promo.setId(UUID.randomUUID());
            return promo;
        });
        when(saverLead.save(any())).thenAnswer(inv -> {
            PromoPopupLead lead = inv.getArgument(0);
            lead.setId(UUID.randomUUID());
            return lead;
        });
    }

    @Test
    void issuesSingleUseCodeForPaidAmountBoundToPhoneAndEmail() {
        CoursePromoOutcome outcome = service.afterPurchase(request(), "1.1.1.1");

        assertEquals(CoursePromoOutcome.Status.READY, outcome.status());
        assertEquals(POPUP_ID, outcome.promo().popupId());
        assertEquals("скидка {discount} на курс", outcome.promo().title());
        assertEquals(500_000L, outcome.promo().discountKopecks());
        assertEquals(COURSE_PRICE, outcome.promo().coursePriceKopecks());
        assertEquals(COURSE_PRICE - 500_000L, outcome.promo().discountedPriceKopecks());
        assertEquals("4567", outcome.promo().phoneTail());
        assertFalse(outcome.promo().repeated());
        assertTrue(outcome.promo().code().startsWith("COURSE-"));

        ArgumentCaptor<PromoCode> saved = ArgumentCaptor.forClass(PromoCode.class);
        verify(saverPromoCode).save(saved.capture());
        PromoCode promo = saved.getValue();
        assertEquals(0, promo.getDiscountPercent());
        assertEquals(500_000L, promo.getDiscountAmountKopecks());
        assertEquals(1, promo.getMaxUses());
        assertEquals(POPUP_ID, promo.getPopupId());
        assertEquals("buyer@example.com", promo.getOwnerEmail());
        assertEquals("9991234567", promo.getOwnerPhoneLast10());
        assertEquals(PaymentProduct.CODE_COURSE_STREAM_2, promo.getProductCode());
        assertNull(promo.getShopSlug());
        assertTrue(promo.isProductOnly());
        assertEquals(90, Duration.between(promo.getValidFrom(), promo.getValidUntil()).toDays());

        ArgumentCaptor<PromoPopupLead> lead = ArgumentCaptor.forClass(PromoPopupLead.class);
        verify(saverLead).save(lead.capture());
        assertEquals(POPUP_ID, lead.getValue().getPopupId());
        assertEquals(42L, lead.getValue().getOrderId());
        assertEquals(promo.getId(), lead.getValue().getPromoCodeId());
        assertEquals("+79991234567", lead.getValue().getPhone());
        assertEquals("anyforms", lead.getValue().getShopSlug());
    }

    @Test
    void nothingIssuedWithoutLivePopup() {
        when(getterPromoPopup.getLive(eq("anyforms"), any())).thenReturn(List.of(
                PromoPopup.builder().id(UUID.randomUUID()).popupType(PromoPopupType.AFTER_PURCHASE).build()));

        assertEquals(CoursePromoOutcome.Status.NONE, service.afterPurchase(request(), null).status());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void awaitingPaymentIsReportedSeparately() {
        order.setPaymentStatus(OrderPaymentStatus.AWAITING_PAYMENT);

        assertEquals(CoursePromoOutcome.Status.AWAITING_PAYMENT, service.afterPurchase(request(), null).status());
    }

    @Test
    void nothingIssuedToClientWhoAlreadyBoughtTheCourse() {
        when(getterTransaction.customerPaidAnyProduct(CoursePromoServiceImpl.COURSE_PRODUCT_CODES,
                "buyer@example.com", "9991234567")).thenReturn(true);

        assertEquals(CoursePromoOutcome.Status.NONE, service.afterPurchase(request(), null).status());
        verify(saverPromoCode, never()).save(any());
        verify(saverLead, never()).save(any());
    }

    @Test
    void clientLimitFromMaxShowsIsRespected() {
        popup.setMaxShows(1);
        when(getterLead.countForClient(POPUP_ID, "buyer@example.com", "9991234567", "")).thenReturn(1L);

        assertEquals(CoursePromoOutcome.Status.NONE, service.afterPurchase(request(), null).status());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void discountIsCappedByPopupAmountAndByCoursePrice() {
        assertEquals(300_000L, CoursePromoServiceImpl.discountFor(500_000L, 300_000L, COURSE_PRICE));
        assertEquals(500_000L, CoursePromoServiceImpl.discountFor(500_000L, null, COURSE_PRICE));
        assertEquals(COURSE_PRICE - 100, CoursePromoServiceImpl.discountFor(2_000_000L, null, COURSE_PRICE));
    }

    @Test
    void repeatedRequestReturnsTheSameCode() {
        PromoCode issued = PromoCode.builder().id(UUID.randomUUID()).code("COURSE-AAAAA").discountPercent(0)
                .discountAmountKopecks(500_000L).active(true).maxUses(1).ownerPhoneLast10("9991234567")
                .productCode(PaymentProduct.CODE_COURSE_STREAM_2).build();
        when(getterLead.getLatestForOrder(POPUP_ID, 42L)).thenReturn(Optional.of(PromoPopupLead.builder()
                .popupId(POPUP_ID).orderId(42L).promoCodeId(issued.getId()).code(issued.getCode()).build()));
        when(getterPromoCode.getById(issued.getId())).thenReturn(Optional.of(issued));

        CoursePromoOutcome outcome = service.afterPurchase(request(), null);

        assertEquals(CoursePromoOutcome.Status.READY, outcome.status());
        assertEquals("COURSE-AAAAA", outcome.promo().code());
        assertTrue(outcome.promo().repeated());
        verify(saverPromoCode, never()).save(any());
    }

    @Test
    void usedCodeIsNotShownAgain() {
        PromoCode issued = PromoCode.builder().id(UUID.randomUUID()).code("COURSE-AAAAA").discountPercent(0)
                .discountAmountKopecks(500_000L).active(true).maxUses(1).build();
        when(getterLead.getLatestForOrder(POPUP_ID, 42L)).thenReturn(Optional.of(PromoPopupLead.builder()
                .popupId(POPUP_ID).orderId(42L).promoCodeId(issued.getId()).code(issued.getCode()).build()));
        when(getterPromoCode.getById(issued.getId())).thenReturn(Optional.of(issued));
        when(checker.exhausted(issued)).thenReturn(true);

        assertEquals(CoursePromoOutcome.Status.NONE, service.afterPurchase(request(), null).status());
    }

    @Test
    void checkoutRejectsAnotherPhone() {
        Optional<String> rejection = service.checkoutRejection(courseCode(), course,
                PromoClient.of("friend@example.com", "+79210000000", null));

        assertTrue(rejection.orElseThrow().contains("другой номер телефона"));
    }

    @Test
    void checkoutRequiresPhoneForPhoneBoundCode() {
        PromoCode promo = courseCode();

        assertTrue(service.checkoutRejection(promo, course, PromoClient.of("buyer@example.com", null, null))
                .orElseThrow().contains("Укажите телефон"));
        assertTrue(service.checkoutRejection(promo, course, PromoClient.of(null, null, null))
                .orElseThrow().contains("укажите телефон"));
    }

    @Test
    void checkoutRejectsClientWhoAlreadyBoughtTheCourse() {
        when(getterTransaction.customerPaidAnyProduct(CoursePromoServiceImpl.COURSE_PRODUCT_CODES,
                "buyer@example.com", "9991234567")).thenReturn(true);

        assertTrue(service.checkoutRejection(courseCode(), course, owner()).orElseThrow().contains("уже куплен"));
    }

    @Test
    void checkoutRejectsUsedCodeAfterTryingToReleaseOwnReservation() {
        PromoCode promo = courseCode();
        when(checker.exhausted(promo)).thenReturn(true);

        assertTrue(service.checkoutRejection(promo, course, owner()).orElseThrow().contains("уже использован"));
        verify(reservationService).releaseOwnReservations(promo, owner());
    }

    @Test
    void checkoutRejectsOtherProduct() {
        PaymentProduct guide = PaymentProduct.builder().code(PaymentProduct.CODE_GUIDE).title("Гайд").build();

        assertTrue(service.checkoutRejection(courseCode(), guide, owner()).orElseThrow().contains("Курс по молдам"));
    }

    @Test
    void ownerPassesCheckoutAndAnonymousPreviewIsAllowed() {
        PromoCode promo = courseCode();

        assertTrue(service.checkoutRejection(promo, course, owner()).isEmpty());
        assertTrue(service.previewRejection(promo, course, PromoClient.of(null, null, null)).isEmpty());
        verify(getterTransaction, never()).countPromoUsesExceptOwnPending(anyString(), any(), anyString(),
                anyString(), anyString());
    }

    @Test
    void previewIgnoresOwnPendingPayment() {
        when(getterTransaction.countPromoUsesExceptOwnPending(eq("COURSE-AAAAA"), any(), eq("buyer@example.com"),
                eq("9991234567"), eq(""))).thenReturn(0L);

        assertTrue(service.previewRejection(courseCode(), course, owner()).isEmpty());
    }

    private static PromoPopupAfterPurchaseRequest request() {
        return PromoPopupAfterPurchaseRequest.builder().orderNumber(ORDER_NUMBER.toLowerCase()).build();
    }

    private static PromoClient owner() {
        return PromoClient.of("buyer@example.com", "+79991234567", null);
    }

    private static PromoCode courseCode() {
        return PromoCode.builder().id(UUID.randomUUID()).code("COURSE-AAAAA").discountPercent(0)
                .discountAmountKopecks(500_000L).active(true).maxUses(1).popupId(POPUP_ID)
                .ownerEmail("buyer@example.com").ownerPhoneLast10("9991234567")
                .productCode(PaymentProduct.CODE_COURSE_STREAM_2).build();
    }

    private static PaymentTransaction transaction(PaymentTransactionStatus status, long amount) {
        return PaymentTransaction.builder().status(status).amount(amount).orderId(42L).build();
    }
}
