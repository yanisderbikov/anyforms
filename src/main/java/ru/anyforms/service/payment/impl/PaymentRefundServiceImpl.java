package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.RefundOrderResult;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelResponse;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.PaymentRefundService;
import ru.anyforms.service.payment.TinkoffService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class PaymentRefundServiceImpl implements PaymentRefundService {

    private final TinkoffService tinkoffService;
    private final GetterTransaction getterTransaction;
    private final SaverTransaction saverTransaction;
    private final OrderRepository orderRepository;

    @Override
    public List<RefundOrderResult> refundOrders(List<Long> orderIds) {
        List<RefundOrderResult> results = new ArrayList<>();
        for (Long orderId : orderIds) {
            results.add(refundOrder(orderId));
        }
        return results;
    }

    private RefundOrderResult refundOrder(Long orderId) {
        try {
            PaymentTransaction transaction = findRefundableTransaction(orderId);
            if (transaction == null) {
                return RefundOrderResult.builder()
                        .orderId(orderId)
                        .success(false)
                        .message("Нет успешного платежа Т-Кассы по этому заказу")
                        .build();
            }

            TinkoffCancelResponse response = tinkoffService.cancel(TinkoffCancelRequest.builder()
                    .paymentId(transaction.getExternalPaymentId())
                    .build());

            transaction.setStatus(PaymentTransactionStatus.CANCELED);
            saverTransaction.save(transaction);
            orderRepository.findById(orderId).ifPresent(order -> markRefunded(order));

            log.info("Возврат по заказу {} выполнен: платёж {}, статус {}",
                    orderId, transaction.getExternalPaymentId(), response.getStatus());
            return RefundOrderResult.builder()
                    .orderId(orderId)
                    .externalPaymentId(transaction.getExternalPaymentId())
                    .amount(transaction.getAmount())
                    .success(true)
                    .message(response.getStatus())
                    .build();
        } catch (Exception e) {
            log.error("Не удалось сделать возврат по заказу {}", orderId, e);
            return RefundOrderResult.builder()
                    .orderId(orderId)
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    private PaymentTransaction findRefundableTransaction(Long orderId) {
        return getterTransaction.getByOrderId(orderId).stream()
                .filter(t -> t.getProvider() == PaymentProvider.TINKOFF)
                .filter(t -> t.getStatus() == PaymentTransactionStatus.SUCCEEDED)
                .findFirst()
                .orElse(null);
    }

    private void markRefunded(Order order) {
        order.setPaymentStatus(OrderPaymentStatus.CANCELED);
        orderRepository.save(order);
    }
}
