package ru.anyforms.repository;

import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterTransaction {
    Optional<PaymentTransaction> getById(UUID id);

    Optional<PaymentTransaction> getByExternalPaymentId(String externalPaymentId);

    List<PaymentTransaction> getByOrderId(Long orderId);

    List<PaymentTransaction> getRecentByProductCode(String productCode, int limit);

    List<PaymentTransaction> getRecentByProductCodes(Collection<String> productCodes, int limit);

    List<PaymentTransaction> getRecentByProviderStatusAndProductCodes(PaymentProvider provider,
                                                                      PaymentTransactionStatus status,
                                                                      Collection<String> productCodes,
                                                                      int limit);

    /** Оплаченные покупки почты по списку продуктов — проверка доступа к обучению */
    List<PaymentTransaction> getPaidByEmailAndProductCodes(String email, Collection<String> productCodes);

    /** Последние транзакции любого провайдера в статусе за окно по updatedAt, свежие сверху */
    List<PaymentTransaction> getRecentByStatusAndProductCodesUpdatedBetween(PaymentTransactionStatus status,
                                                                            Collection<String> productCodes,
                                                                            Instant from,
                                                                            Instant to,
                                                                            int limit);

    /** Транзакции любого провайдера в статусе за окно по updatedAt (момент подтверждения оплаты) */
    List<PaymentTransaction> getByStatusAndProductCodesUpdatedBetween(PaymentTransactionStatus status,
                                                                      Collection<String> productCodes,
                                                                      Instant from,
                                                                      Instant to);

    List<ProductSalesRow> getSalesByProductCodes(PaymentTransactionStatus status,
                                                 Collection<String> productCodes,
                                                 Instant from,
                                                 Instant to);

    boolean promoUsedByCustomer(String promoCode, String email, String phoneLast10);

    /** Транзакции провайдера и продукта в статусе, созданные в окне [from, to], старые сверху */
    List<PaymentTransaction> getByProviderStatusAndProductCodeCreatedBetween(PaymentProvider provider,
                                                                             PaymentTransactionStatus status,
                                                                             String productCode,
                                                                             Instant from,
                                                                             Instant to);

    /**
     * Оплатил ли клиент (по почте или последним 10 цифрам телефона, телефон берётся из заказа
     * или транзакции) другой заказ этого продукта, созданный не раньше since. Возврат тоже
     * считается оплатой: деньги списывались.
     */
    boolean customerPaidProductSince(UUID excludeTransactionId,
                                     String productCode,
                                     String email,
                                     String phoneLast10,
                                     Instant since);
}
