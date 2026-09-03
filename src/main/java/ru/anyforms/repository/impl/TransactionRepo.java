package ru.anyforms.repository.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.ProductSalesRow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface TransactionRepo extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByExternalPaymentId(String externalPaymentId);

    List<PaymentTransaction> findByOrderId(Long orderId);

    List<PaymentTransaction> findByProductCodeOrderByCreatedAtDesc(String productCode, Pageable pageable);

    List<PaymentTransaction> findByProductCodeInOrderByCreatedAtDesc(Collection<String> productCodes, Pageable pageable);

    List<PaymentTransaction> findByProviderAndStatusAndProductCodeInOrderByCreatedAtDesc(PaymentProvider provider,
                                                                                        PaymentTransactionStatus status,
                                                                                        Collection<String> productCodes,
                                                                                        Pageable pageable);

    /** Оплаченные покупки почты — для проверки доступа к платформе обучения */
    List<PaymentTransaction> findByEmailIgnoreCaseAndStatusAndProductCodeInOrderByCreatedAtDesc(
            String email,
            PaymentTransactionStatus status,
            Collection<String> productCodes);

    @Query("""
            SELECT t FROM PaymentTransaction t
            WHERE t.status = :status
              AND t.productCode IN :productCodes
              AND t.updatedAt >= :from
              AND t.updatedAt < :to
            ORDER BY t.updatedAt
            """)
    List<PaymentTransaction> findByStatusProductCodesAndUpdatedAtBetween(
            @Param("status") PaymentTransactionStatus status,
            @Param("productCodes") Collection<String> productCodes,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT t FROM PaymentTransaction t
            WHERE t.status = :status
              AND t.productCode IN :productCodes
              AND t.updatedAt >= :from
              AND t.updatedAt < :to
            ORDER BY t.updatedAt DESC
            """)
    List<PaymentTransaction> findRecentByStatusProductCodesAndUpdatedAtBetween(
            @Param("status") PaymentTransactionStatus status,
            @Param("productCodes") Collection<String> productCodes,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            SELECT t.productCode AS productCode,
                   COUNT(t) AS quantity,
                   SUM(t.amount) AS amountKopecks
            FROM PaymentTransaction t
            WHERE t.status = :status
              AND t.productCode IN :productCodes
              AND t.updatedAt >= :from
              AND t.updatedAt < :to
            GROUP BY t.productCode
            """)
    List<ProductSalesRow> aggregateByProductCode(@Param("status") PaymentTransactionStatus status,
                                                 @Param("productCodes") Collection<String> productCodes,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM payment_transaction pt
                LEFT JOIN orders o ON o.id = pt.order_id
                WHERE pt.promo_code = :promoCode
                  AND pt.status IN ('SUCCEEDED', 'REFUNDED')
                  AND (lower(pt.email) = lower(:email)
                       OR (:phoneLast10 <> ''
                           AND right(regexp_replace(coalesce(o.contact_phone, ''), '\\D', '', 'g'), 10) = :phoneLast10))
            )
            """, nativeQuery = true)
    boolean promoUsedByCustomer(@Param("promoCode") String promoCode,
                                @Param("email") String email,
                                @Param("phoneLast10") String phoneLast10);

    List<PaymentTransaction> findByProviderAndStatusAndProductCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
            PaymentProvider provider,
            PaymentTransactionStatus status,
            String productCode,
            Instant from,
            Instant to);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM payment_transaction pt
                LEFT JOIN orders o ON o.id = pt.order_id
                WHERE pt.id <> :excludeId
                  AND pt.product_code = :productCode
                  AND pt.status IN ('SUCCEEDED', 'REFUNDED')
                  AND pt.created_at >= :since
                  AND ((:email <> '' AND lower(pt.email) = lower(:email))
                       OR (:phoneLast10 <> ''
                           AND right(regexp_replace(coalesce(o.contact_phone, pt.contact_phone, ''), '\\D', '', 'g'), 10)
                               = :phoneLast10))
            )
            """, nativeQuery = true)
    boolean customerPaidProductSince(@Param("excludeId") UUID excludeId,
                                     @Param("productCode") String productCode,
                                     @Param("email") String email,
                                     @Param("phoneLast10") String phoneLast10,
                                     @Param("since") Instant since);
}
