package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.YooKassaPaymentResponse;
import ru.anyforms.dto.payment.yookassa.CreatePaymentRequest;

public interface YooKassaService {
    YooKassaPaymentResponse createPayment(CreatePaymentRequest request);
}
