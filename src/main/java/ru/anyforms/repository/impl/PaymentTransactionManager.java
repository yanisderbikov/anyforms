package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.ProductSalesRow;
import ru.anyforms.repository.SaverTransaction;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class PaymentTransactionManager implements GetterTransaction, SaverTransaction {

    private final TransactionRepo transactionRepo;

    @Override
    public Optional<PaymentTransaction> getById(UUID id) {
        try {
            return transactionRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

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
    public Optional<PaymentTransaction> getByExternalPaymentIdForUpdate(String externalPaymentId) {
        try {
            return transactionRepo.findByExternalPaymentIdForUpdate(externalPaymentId);
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
    public List<PaymentTransaction> getRecentByProductCodes(Collection<String> productCodes, int limit) {
        try {
            return transactionRepo.findByProductCodeInOrderByCreatedAtDesc(productCodes, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getPaidByEmailAndProductCodes(String email, Collection<String> productCodes) {
        try {
            return transactionRepo.findByEmailIgnoreCaseAndStatusAndProductCodeInOrderByCreatedAtDesc(
                    email, PaymentTransactionStatus.SUCCEEDED, productCodes);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getRecentByProviderStatusAndProductCodes(PaymentProvider provider,
                                                                             PaymentTransactionStatus status,
                                                                             Collection<String> productCodes,
                                                                             int limit) {
        try {
            return transactionRepo.findByProviderAndStatusAndProductCodeInOrderByCreatedAtDesc(
                    provider, status, productCodes, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getRecentByStatusAndProductCodesUpdatedBetween(PaymentTransactionStatus status,
                                                                                   Collection<String> productCodes,
                                                                                   Instant from,
                                                                                   Instant to,
                                                                                   int limit) {
        try {
            return transactionRepo.findRecentByStatusProductCodesAndUpdatedAtBetween(
                    status, productCodes, from, to, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getByStatusAndProductCodesUpdatedBetween(PaymentTransactionStatus status,
                                                                             Collection<String> productCodes,
                                                                             Instant from,
                                                                             Instant to) {
        try {
            return transactionRepo.findByStatusProductCodesAndUpdatedAtBetween(
                    status, productCodes, from, to);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<ProductSalesRow> getSalesByProductCodes(PaymentTransactionStatus status,
                                                        Collection<String> productCodes,
                                                        Instant from,
                                                        Instant to) {
        try {
            return transactionRepo.aggregateByProductCode(status, productCodes, from, to);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getByProviderStatusAndProductCodeCreatedBetween(PaymentProvider provider,
                                                                                    PaymentTransactionStatus status,
                                                                                    String productCode,
                                                                                    Instant from,
                                                                                    Instant to) {
        try {
            return transactionRepo.findByProviderAndStatusAndProductCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                    provider, status, productCode, from, to);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean customerPaidProductSince(UUID excludeTransactionId, String productCode, String email,
                                            String phoneLast10, Instant since) {
        try {
            return transactionRepo.customerPaidProductSince(excludeTransactionId, productCode,
                    email == null ? "" : email, phoneLast10 == null ? "" : phoneLast10, since);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean promoUsedByCustomer(String promoCode, String email, String phoneLast10, String deviceId) {
        try {
            return transactionRepo.promoUsedByCustomer(promoCode, email, phoneLast10, deviceId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public long countPromoUses(String promoCode, Instant pendingSince) {
        try {
            return transactionRepo.countPromoUses(promoCode, pendingSince);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<PaymentTransaction> getPendingByPromoCodeAndDevice(String promoCode, String deviceId, Instant since) {
        if (deviceId == null || deviceId.isBlank()) {
            return List.of();
        }
        try {
            return transactionRepo.findPendingByPromoCodeAndDevice(promoCode, deviceId, since);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public boolean popupCodeUsedByCustomer(UUID popupId, String email, String phoneLast10, String deviceId) {
        try {
            return transactionRepo.popupCodeUsedByCustomer(popupId, email, phoneLast10, deviceId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Map<UUID, Long> countSucceededByPopup() {
        try {
            Map<UUID, Long> counts = new HashMap<>();
            for (Object[] row : transactionRepo.countSucceededByPopup()) {
                counts.put(UUID.fromString(String.valueOf(row[0])), ((Number) row[1]).longValue());
            }
            return counts;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Map<String, Long> countSucceededByPromoCodes(Collection<String> promoCodes) {
        if (promoCodes == null || promoCodes.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Long> counts = new HashMap<>();
            for (Object[] row : transactionRepo.countSucceededByPromoCodes(promoCodes)) {
                counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            }
            return counts;
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public long countSucceededByPromoCode(String promoCode) {
        try {
            return transactionRepo.countSucceededByPromoCode(promoCode);
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
