package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.CartPurchaseRequest;
import ru.anyforms.dto.payment.PaymentUrlResponse;

public interface CartPurchaseService {
    PaymentUrlResponse purchase(CartPurchaseRequest request);
}
