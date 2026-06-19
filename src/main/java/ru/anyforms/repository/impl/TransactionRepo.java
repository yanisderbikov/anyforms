package ru.anyforms.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.payment.PaymentTransaction;

import java.util.Optional;
import java.util.UUID;

@Repository
interface TransactionRepo extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByExternalPaymentId(UUID externalPaymentId);
}
