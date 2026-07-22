package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.email.EmailTaskPayload;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.service.payment.PaymentFulfillmentService;
import ru.anyforms.service.task.TaskAdder;

@Service
@RequiredArgsConstructor
@Slf4j
class PaymentFulfillmentServiceImpl implements PaymentFulfillmentService {

    private final TaskAdder taskAdder;
    private final MarketplaceFulfillmentService marketplaceFulfillmentService;

    @Override
    public void fulfill(PaymentTransaction transaction) {
        if (PaymentProduct.CODE_MARKETPLACE_CART.equals(transaction.getProductCode())) {
            marketplaceFulfillmentService.fulfill(transaction);
            return;
        }
        if (PaymentProduct.CODE_MANUAL_INVOICE.equals(transaction.getProductCode())) {
            log.info("Оплачен ручной счёт {} ({}, {} коп.)",
                    transaction.getExternalPaymentId(), transaction.getContactName(), transaction.getAmount());
            return;
        }

        EmailTaskPayload payload = EmailTaskPayload.builder()
                .to(transaction.getEmail())
                .productCode(transaction.getProductCode())
                .build();
        taskAdder.addTask(payload);
        log.info("Поставлена таска на письмо о покупке продукта {} на {} (транзакция {})",
                transaction.getProductCode(), transaction.getEmail(), transaction.getId());
    }

    @Override
    public void cancel(PaymentTransaction transaction) {
        if (PaymentProduct.CODE_MARKETPLACE_CART.equals(transaction.getProductCode())) {
            marketplaceFulfillmentService.cancel(transaction);
        }
        // Для курса/гайда при отмене ничего делать не нужно.
    }

    @Override
    public void refund(PaymentTransaction transaction) {
        if (PaymentProduct.CODE_MARKETPLACE_CART.equals(transaction.getProductCode())) {
            marketplaceFulfillmentService.refund(transaction);
        }
        // Для курса/гайда при возврате ничего делать не нужно.
    }
}
