package ru.anyforms.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import ru.anyforms.model.payment.Currency;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.ProductSalesRow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка агрегирующего запроса продаж на живом Postgres: границы периода,
 * фильтр по статусу и продукту, маппинг проекции.
 * <p>
 * Требует поднятую БД, поэтому по умолчанию выключен. Запуск:
 * <pre>
 * mvn test -Dtest=TransactionRepoSalesQueryTest -DsalesQueryDb=true \
 *   -Dsales.query.db.url=jdbc:postgresql://localhost:5432/anyforms_test
 * </pre>
 */
@DataJpaTest
@EnabledIfSystemProperty(named = "salesQueryDb", matches = "true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${sales.query.db.url:jdbc:postgresql://localhost:5471/anyforms_test}",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
})
class TransactionRepoSalesQueryTest {

    private static final Instant DAY_FROM = Instant.parse("2026-07-27T21:00:00Z");
    private static final Instant DAY_TO = Instant.parse("2026-07-28T21:00:00Z");

    @Autowired
    private TransactionRepo transactionRepo;

    @PersistenceContext
    private EntityManager entityManager;

    private void save(String productCode, PaymentTransactionStatus status, long amount, Instant updatedAt) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .status(status)
                .provider(PaymentProvider.YOOKASSA)
                .externalPaymentId("ext-" + productCode + "-" + status + "-" + updatedAt.toEpochMilli())
                .productCode(productCode)
                .amount(amount)
                .currency(Currency.RUB)
                .build();
        transactionRepo.saveAndFlush(transaction);
        entityManager.createNativeQuery("UPDATE payment_transaction SET updated_at = ?1 WHERE id = ?2")
                .setParameter(1, updatedAt)
                .setParameter(2, transaction.getId())
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    void aggregatesOnlySucceededTrainingSalesInsidePeriod() {
        save(PaymentProduct.CODE_GUIDE, PaymentTransactionStatus.SUCCEEDED, 149_000L,
                Instant.parse("2026-07-28T05:00:00Z"));
        save(PaymentProduct.CODE_GUIDE, PaymentTransactionStatus.SUCCEEDED, 149_000L,
                Instant.parse("2026-07-28T20:59:59Z"));
        save(PaymentProduct.CODE_COURSE, PaymentTransactionStatus.SUCCEEDED, 990_000L,
                Instant.parse("2026-07-27T21:00:00Z"));
        save(PaymentProduct.CODE_GUIDE, PaymentTransactionStatus.PENDING, 149_000L,
                Instant.parse("2026-07-28T06:00:00Z"));
        save(PaymentProduct.CODE_GUIDE, PaymentTransactionStatus.REFUNDED, 149_000L,
                Instant.parse("2026-07-28T07:00:00Z"));
        save(PaymentProduct.CODE_MARKETPLACE_CART, PaymentTransactionStatus.SUCCEEDED, 500_000L,
                Instant.parse("2026-07-28T08:00:00Z"));
        save(PaymentProduct.CODE_GUIDE, PaymentTransactionStatus.SUCCEEDED, 149_000L,
                Instant.parse("2026-07-27T20:59:59Z"));
        save(PaymentProduct.CODE_GUIDE, PaymentTransactionStatus.SUCCEEDED, 149_000L,
                Instant.parse("2026-07-28T21:00:00Z"));

        Map<String, ProductSalesRow> rows = transactionRepo.aggregateByProductCode(
                        PaymentTransactionStatus.SUCCEEDED,
                        List.of(PaymentProduct.CODE_GUIDE, PaymentProduct.CODE_COURSE,
                                PaymentProduct.CODE_COURSE_PERSONAL),
                        DAY_FROM, DAY_TO)
                .stream()
                .collect(Collectors.toMap(ProductSalesRow::getProductCode, Function.identity()));

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(PaymentProduct.CODE_GUIDE).getQuantity());
        assertEquals(298_000L, rows.get(PaymentProduct.CODE_GUIDE).getAmountKopecks());
        assertEquals(1, rows.get(PaymentProduct.CODE_COURSE).getQuantity());
        assertEquals(990_000L, rows.get(PaymentProduct.CODE_COURSE).getAmountKopecks());
        assertNull(rows.get(PaymentProduct.CODE_COURSE_PERSONAL));
    }

    @Test
    void returnsEmptyListWhenNothingSold() {
        assertTrue(transactionRepo.aggregateByProductCode(
                PaymentTransactionStatus.SUCCEEDED,
                List.of(PaymentProduct.CODE_GUIDE),
                DAY_FROM, DAY_TO).isEmpty());
    }
}
