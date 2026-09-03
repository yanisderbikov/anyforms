package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.anyforms.model.Order;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.util.PhoneUtil;

import java.time.Duration;

/**
 * Определяет, что неуспешная оплата корзины — просто повторная попытка: тот же клиент
 * (по почте или последним 10 цифрам телефона) после неё оплатил другой заказ маркетплейса.
 * Тогда дожимать некого и сделку о неудачной оплате заводить не нужно.
 * <p>
 * {@link #DOUBLE_CLICK_TOLERANCE} покрывает двойной клик по кнопке оплаты, когда оплаченной
 * оказывается транзакция, созданная на секунды раньше неуспешной. Успех до неуспешной попытки
 * не считается: это другая покупка, а новую корзину надо дожимать.
 */
@Component
@RequiredArgsConstructor
class MarketplaceRepaymentChecker {

    static final Duration DOUBLE_CLICK_TOLERANCE = Duration.ofMinutes(1);

    private final GetterTransaction getterTransaction;
    private final OrderRepository orderRepository;

    boolean paidAnotherOrderAfter(PaymentTransaction transaction) {
        String phone = transaction.getContactPhone();
        if (phone == null && transaction.getOrderId() != null) {
            phone = orderRepository.findById(transaction.getOrderId())
                    .map(Order::getContactPhone)
                    .orElse(null);
        }
        return getterTransaction.customerPaidProductSince(
                transaction.getId(), PaymentProduct.CODE_MARKETPLACE_CART,
                transaction.getEmail(), PhoneUtil.last10(phone),
                transaction.getCreatedAt().minus(DOUBLE_CLICK_TOLERANCE));
    }
}
