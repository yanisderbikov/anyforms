package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTransaction;

import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class PaymentTransactionManager implements GetterTransaction, SaverTransaction {

    private final TransactionRepo transactionRepo;

    @Override
    public Optional<PaymentTransaction> getByExternalPaymentId(UUID externalPaymentId) {
        try {
            return transactionRepo.findByExternalPaymentId(externalPaymentId);
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
