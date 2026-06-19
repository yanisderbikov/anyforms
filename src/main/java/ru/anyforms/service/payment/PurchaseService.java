package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.PaymentUrlResponse;
import ru.anyforms.dto.payment.PurchaseRequest;

public interface PurchaseService {
    PaymentUrlResponse purchase(PurchaseRequest request);
}
