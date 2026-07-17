package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.CartPurchaseRequest;
import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.PromoCheckResponse;

public interface CartPurchaseService {
    PaymentUrlResponse purchase(CartPurchaseRequest request);

    PromoCheckResponse checkPromo(String code, String email, String phone);
}
