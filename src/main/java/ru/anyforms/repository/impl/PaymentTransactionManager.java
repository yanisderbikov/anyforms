package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTransaction;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class PaymentTransactionManager implements GetterTransaction, SaverTransaction {

    private final TransactionRepo transactionRepo;

    @Override
    public Optional<PaymentTransaction> getByExternalPaymentId(String externalPaymentId) {
        try {
            return transactionRepo.findByExternalPaymentId(externalPaymentId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getByOrderId(Long orderId) {
        try {
            return transactionRepo.findByOrderId(orderId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getRecentByProductCode(String productCode, int limit) {
        try {
            return transactionRepo.findByProductCodeOrderByCreatedAtDesc(productCode, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean promoUsedByCustomer(String promoCode, String email, String phoneLast10) {
        try {
            return transactionRepo.promoUsedByCustomer(promoCode, email, phoneLast10);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        try {
            return transactionRepo.save(transaction);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
