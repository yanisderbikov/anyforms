package ru.anyforms.repository.impl;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.externalPaymentId = :externalPaymentId")
    Optional<PaymentTransaction> findByExternalPaymentIdForUpdate(
            @Param("externalPaymentId") String externalPaymentId);

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
                  AND ((:email <> '' AND lower(coalesce(pt.email, '')) = lower(:email))
                       OR (:phoneLast10 <> ''
                           AND right(regexp_replace(coalesce(o.contact_phone, pt.contact_phone, ''), '\\D', '', 'g'), 10) = :phoneLast10)
                       OR (:deviceId <> '' AND coalesce(o.device_id, '') = :deviceId))
            )
            """, nativeQuery = true)
    boolean promoUsedByCustomer(@Param("promoCode") String promoCode,
                                @Param("email") String email,
                                @Param("phoneLast10") String phoneLast10,
                                @Param("deviceId") String deviceId);

    @Query(value = """
            SELECT COUNT(*)
            FROM payment_transaction pt
            WHERE pt.promo_code = :promoCode
              AND (pt.status IN ('SUCCEEDED', 'REFUNDED')
                   OR (pt.status = 'PENDING' AND pt.created_at >= :pendingSince))
            """, nativeQuery = true)
    long countPromoUses(@Param("promoCode") String promoCode, @Param("pendingSince") Instant pendingSince);

    @Query(value = """
            SELECT COUNT(*)
            FROM payment_transaction pt
            LEFT JOIN orders o ON o.id = pt.order_id
            WHERE pt.promo_code = :promoCode
              AND (pt.status IN ('SUCCEEDED', 'REFUNDED')
                   OR (pt.status = 'PENDING'
                       AND pt.created_at >= :pendingSince
                       AND NOT (pt.provider = 'TINKOFF' AND coalesce(o.device_id, '') = :deviceId)))
            """, nativeQuery = true)
    long countPromoUsesExceptDevicePending(@Param("promoCode") String promoCode,
                                           @Param("pendingSince") Instant pendingSince,
                                           @Param("deviceId") String deviceId);

    @Query(value = """
            SELECT pt.external_payment_id
            FROM payment_transaction pt
            JOIN orders o ON o.id = pt.order_id
            WHERE pt.promo_code = :promoCode
              AND pt.status = 'PENDING'
              AND pt.provider = 'TINKOFF'
              AND pt.created_at >= :since
              AND o.device_id = :deviceId
            ORDER BY pt.created_at
            """, nativeQuery = true)
    List<String> findPendingTinkoffPaymentIds(@Param("promoCode") String promoCode,
                                              @Param("deviceId") String deviceId,
                                              @Param("since") Instant since);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM payment_transaction pt
                JOIN promo_code pc ON pc.code = pt.promo_code
                LEFT JOIN orders o ON o.id = pt.order_id
                WHERE pc.popup_id = :popupId
                  AND pt.status IN ('SUCCEEDED', 'REFUNDED')
                  AND ((:email <> '' AND lower(coalesce(pt.email, '')) = lower(:email))
                       OR (:phoneLast10 <> ''
                           AND right(regexp_replace(coalesce(o.contact_phone, pt.contact_phone, ''), '\\D', '', 'g'), 10) = :phoneLast10)
                       OR (:deviceId <> '' AND coalesce(o.device_id, '') = :deviceId))
            )
            """, nativeQuery = true)
    boolean popupCodeUsedByCustomer(@Param("popupId") UUID popupId,
                                    @Param("email") String email,
                                    @Param("phoneLast10") String phoneLast10,
                                    @Param("deviceId") String deviceId);

    @Query(value = """
            SELECT CAST(pc.popup_id AS VARCHAR) AS popup_id, COUNT(*) AS uses
            FROM payment_transaction pt
            JOIN promo_code pc ON pc.code = pt.promo_code
            WHERE pc.popup_id IS NOT NULL
              AND pt.status = 'SUCCEEDED'
            GROUP BY pc.popup_id
            """, nativeQuery = true)
    List<Object[]> countSucceededByPopup();

    @Query(value = """
            SELECT COUNT(*) FROM payment_transaction pt
            WHERE pt.promo_code = :promoCode AND pt.status = 'SUCCEEDED'
            """, nativeQuery = true)
    long countSucceededByPromoCode(@Param("promoCode") String promoCode);

    @Query(value = """
            SELECT pt.promo_code AS promo_code, COUNT(*) AS uses
            FROM payment_transaction pt
            WHERE pt.status = 'SUCCEEDED'
              AND pt.promo_code IN (:promoCodes)
            GROUP BY pt.promo_code
            """, nativeQuery = true)
    List<Object[]> countSucceededByPromoCodes(@Param("promoCodes") Collection<String> promoCodes);

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
