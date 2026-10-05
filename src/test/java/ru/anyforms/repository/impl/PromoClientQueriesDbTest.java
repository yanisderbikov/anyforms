package ru.anyforms.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.OrderSource;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupLead;
import ru.anyforms.model.promo.PromoPopupType;
import ru.anyforms.model.promo.PromoPopupView;
import ru.anyforms.repository.OrderRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@EnabledIfSystemProperty(named = "promoQueryDb", matches = "true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${promo.query.db.url:jdbc:postgresql://localhost:5471/anyforms_test}",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
})
class PromoClientQueriesDbTest {

    private static final String DEVICE = "6f1c7a52-8b1e-4c39-9d7e-0a2b3c4d5e6f";
    private static final String OTHER_DEVICE = "0aa1bb2c-3dd4-4ee5-8ff6-112233445566";

    @Autowired
    private TransactionRepo transactionRepo;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PromoPopupRepo promoPopupRepo;

    @Autowired
    private PromoCodeRepo promoCodeRepo;

    @Autowired
    private PromoPopupLeadRepo promoPopupLeadRepo;

    @Autowired
    private PromoPopupViewRepo promoPopupViewRepo;

    @PersistenceContext
    private EntityManager entityManager;

    private Order order(String email, String phone, String deviceId, OrderPaymentStatus status) {
        Order order = new Order();
        order.setRetail(true);
        order.setSource(OrderSource.MARKETPLACE);
        order.setPaymentStatus(status);
        order.setEmail(email);
        order.setContactPhone(phone);
        order.setDeviceId(deviceId);
        return orderRepository.saveAndFlush(order);
    }

    private PaymentTransaction payment(Order order, String promoCode, PaymentTransactionStatus status, Instant createdAt) {
        PaymentTransaction transaction = transactionRepo.saveAndFlush(PaymentTransaction.builder()
                .status(status)
                .provider(PaymentProvider.YOOKASSA)
                .externalPaymentId("ext-" + UUID.randomUUID())
                .productCode(PaymentProduct.CODE_MARKETPLACE_CART)
                .amount(100_000L)
                .currency(Currency.RUB)
                .email(order.getEmail())
                .contactPhone(order.getContactPhone())
                .orderId(order.getId())
                .promoCode(promoCode)
                .build());
        entityManager.createNativeQuery("UPDATE payment_transaction SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, transaction.getId())
                .executeUpdate();
        entityManager.clear();
        return transaction;
    }

    private PromoPopup uniquePopup() {
        return promoPopupRepo.saveAndFlush(PromoPopup.builder()
                .name("Одноразовый")
                .popupType(PromoPopupType.UNIQUE_CODE)
                .active(true)
                .priority(0)
                .shopSlug("anyforms")
                .title("скидка")
                .buttonText("Продолжить покупки")
                .delaySeconds(15)
                .repeatAfterHours(24)
                .discountPercent(10)
                .codePrefix("ONE")
                .codeTtlDays(7)
                .firstOrderOnly(true)
                .hideForKnownContacts(true)
                .amoTaskDeadlineMinutes(60)
                .build());
    }

    @Test
    void migrationsMatchEntitiesAndOrdersAreFoundByDevice() {
        order("a@b.ru", "+7 999 111-22-33", DEVICE, OrderPaymentStatus.PAID);
        order("late@b.ru", "+79990000000", OTHER_DEVICE, OrderPaymentStatus.AWAITING_PAYMENT);

        assertTrue(orderRepository.existsRetailOrderByCustomer("", "", DEVICE));
        assertTrue(orderRepository.existsRetailOrderByCustomer("A@B.RU", "", ""));
        assertTrue(orderRepository.existsRetailOrderByCustomer("", "9991112233", ""));
        assertFalse(orderRepository.existsRetailOrderByCustomer("", "", OTHER_DEVICE));
        assertFalse(orderRepository.existsRetailOrderByCustomer("", "", ""));
    }

    @Test
    void singleUseCodeCountsPaidUsesAndForeignPendingReservations() {
        Instant now = Instant.now();
        Order buyer = order("buyer@b.ru", "+79991234567", DEVICE, OrderPaymentStatus.AWAITING_PAYMENT);
        Order stranger = order("x@b.ru", "+79210000000", OTHER_DEVICE, OrderPaymentStatus.AWAITING_PAYMENT);

        payment(buyer, "ONE-AAAAA", PaymentTransactionStatus.PENDING, now);
        assertEquals(0, transactionRepo.countPromoUsesExceptCustomerPending("ONE-AAAAA", "buyer@b.ru",
                "9991234567", DEVICE, now.minus(Duration.ofMinutes(30))));
        assertEquals(1, transactionRepo.countPromoUsesExceptCustomerPending("ONE-AAAAA", "x@b.ru",
                "9210000000", OTHER_DEVICE, now.minus(Duration.ofMinutes(30))));

        payment(stranger, "ONE-BBBBB", PaymentTransactionStatus.PENDING, now.minus(Duration.ofHours(2)));
        assertEquals(0, transactionRepo.countPromoUsesExceptCustomerPending("ONE-BBBBB", "buyer@b.ru",
                "9991234567", DEVICE, now.minus(Duration.ofMinutes(30))));

        payment(stranger, "ONE-BBBBB", PaymentTransactionStatus.SUCCEEDED, now);
        assertEquals(1, transactionRepo.countPromoUsesExceptCustomerPending("ONE-BBBBB", "buyer@b.ru",
                "9991234567", DEVICE, now.minus(Duration.ofMinutes(30))));
        assertTrue(transactionRepo.promoUsedByCustomer("ONE-BBBBB", "", "", OTHER_DEVICE));
        assertTrue(transactionRepo.promoUsedByCustomer("ONE-BBBBB", "X@B.RU", "", ""));
        assertFalse(transactionRepo.promoUsedByCustomer("ONE-BBBBB", "buyer@b.ru", "9991234567", DEVICE));
        assertEquals(1, transactionRepo.countSucceededByPromoCode("ONE-BBBBB"));
    }

    @Test
    void popupDiscountIsOncePerClientAcrossItsCodes() {
        PromoPopup popup = uniquePopup();
        promoCodeRepo.saveAndFlush(PromoCode.builder().code("ONE-CCCCC").discountPercent(10).active(true)
                .popupId(popup.getId()).ownerDeviceId(DEVICE).maxUses(1).build());
        promoCodeRepo.saveAndFlush(PromoCode.builder().code("ONE-DDDDD").discountPercent(10).active(true)
                .popupId(popup.getId()).ownerDeviceId(OTHER_DEVICE).maxUses(1).build());
        Order order = order("buyer@b.ru", "+79991234567", DEVICE, OrderPaymentStatus.PAID);
        payment(order, "ONE-CCCCC", PaymentTransactionStatus.SUCCEEDED, Instant.now());

        assertTrue(transactionRepo.popupCodeUsedByCustomer(popup.getId(), "", "9991234567", OTHER_DEVICE));
        assertTrue(transactionRepo.popupCodeUsedByCustomer(popup.getId(), "", "", DEVICE));
        assertFalse(transactionRepo.popupCodeUsedByCustomer(popup.getId(), "new@b.ru", "9210000000", OTHER_DEVICE));
        assertFalse(transactionRepo.popupCodeUsedByCustomer(UUID.randomUUID(), "buyer@b.ru", "", ""));

        List<Object[]> used = transactionRepo.countSucceededByPopup();
        assertEquals(1, used.size());
        assertEquals(popup.getId().toString(), String.valueOf(used.get(0)[0]));
        assertEquals(1L, ((Number) used.get(0)[1]).longValue());
    }

    @Test
    void leadsAndViewsAreFoundByDevice() {
        PromoPopup popup = uniquePopup();
        PromoCode promo = promoCodeRepo.saveAndFlush(PromoCode.builder().code("ONE-EEEEE").discountPercent(10)
                .active(true).popupId(popup.getId()).ownerDeviceId(DEVICE).maxUses(1).build());
        promoPopupLeadRepo.saveAndFlush(PromoPopupLead.builder().popupId(popup.getId()).promoCodeId(promo.getId())
                .code(promo.getCode()).shopSlug("anyforms").deviceId(DEVICE).build());
        promoPopupViewRepo.saveAndFlush(PromoPopupView.builder().popupId(popup.getId()).deviceId(DEVICE).build());
        promoPopupViewRepo.saveAndFlush(PromoPopupView.builder().popupId(popup.getId()).deviceId(DEVICE).build());
        promoPopupViewRepo.saveAndFlush(PromoPopupView.builder().popupId(popup.getId()).deviceId(OTHER_DEVICE).build());

        assertEquals(1, promoPopupLeadRepo.findForClient(popup.getId(), "", "", DEVICE, PageRequest.of(0, 1)).size());
        assertTrue(promoPopupLeadRepo.findForClient(popup.getId(), "a@b.ru", "9991112233", OTHER_DEVICE,
                PageRequest.of(0, 1)).isEmpty());
        assertEquals(2, promoPopupViewRepo.countByPopupIdAndDeviceId(popup.getId(), DEVICE));
        assertTrue(promoPopupViewRepo.findFirstByPopupIdAndDeviceIdOrderByCreatedAtDesc(popup.getId(), DEVICE)
                .isPresent());
        Object[] stats = promoPopupViewRepo.statsGroupedByPopup().get(0);
        assertEquals(3L, ((Number) stats[1]).longValue());
        assertEquals(2L, ((Number) stats[2]).longValue());
    }
}
