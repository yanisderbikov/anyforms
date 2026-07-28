package ru.anyforms.repository;

import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GetterTransaction {
    Optional<PaymentTransaction> getByExternalPaymentId(String externalPaymentId);

    List<PaymentTransaction> getByOrderId(Long orderId);

    List<PaymentTransaction> getRecentByProductCode(String productCode, int limit);

    List<PaymentTransaction> getRecentByProductCodes(Collection<String> productCodes, int limit);

    List<PaymentTransaction> getRecentByProviderStatusAndProductCodes(PaymentProvider provider,
                                                                      PaymentTransactionStatus status,
                                                                      Collection<String> productCodes,
                                                                      int limit);

    boolean promoUsedByCustomer(String promoCode, String email, String phoneLast10);
}
