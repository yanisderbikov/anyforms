package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.email.EmailTaskPayload;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.service.payment.PaymentFulfillmentService;
import ru.anyforms.service.task.TaskAdder;

@Service
@RequiredArgsConstructor
@Slf4j
class PaymentFulfillmentServiceImpl implements PaymentFulfillmentService {

    private final TaskAdder taskAdder;

    @Override
    public void fulfill(PaymentTransaction transaction) {
        EmailTaskPayload payload = EmailTaskPayload.builder()
                .to(transaction.getEmail())
                .productCode(transaction.getProductCode())
                .build();
        taskAdder.addTask(payload);
        log.info("Поставлена таска на письмо о покупке продукта {} на {} (транзакция {})",
                transaction.getProductCode(), transaction.getEmail(), transaction.getId());
    }
}
