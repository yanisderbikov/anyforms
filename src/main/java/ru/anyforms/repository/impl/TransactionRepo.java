package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.payment.PaymentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface TransactionRepo extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByExternalPaymentId(String externalPaymentId);

    List<PaymentTransaction> findByOrderId(Long orderId);

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
}
