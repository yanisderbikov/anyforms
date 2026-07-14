package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.RefundOrderResult;

import java.util.List;

public interface PaymentRefundService {
    List<RefundOrderResult> refundOrders(List<Long> orderIds);
}
