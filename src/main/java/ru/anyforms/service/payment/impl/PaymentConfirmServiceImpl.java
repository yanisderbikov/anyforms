package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.YooKassaWebhookBody;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PaymentFulfillmentService;
import ru.anyforms.service.payment.PaymentStatusConverter;

@Service
@RequiredArgsConstructor
@Slf4j
class PaymentConfirmServiceImpl implements PaymentConfirmService {

    private final GetterTransaction getterTransaction;
    private final SaverTransaction saverTransaction;
    private final PaymentStatusConverter paymentStatusConverter;
    private final PaymentFulfillmentService paymentFulfillmentService;

    @Override
    public boolean confirm(YooKassaWebhookBody webhookBody) {
        try {
            PaymentTransaction transaction = getterTransaction
                    .getByExternalPaymentId(webhookBody.getData().getId())
                    .orElseThrow(() -> new RuntimeException(
                            "Транзакция не найдена по external id: " + webhookBody.getData().getId()));

            PaymentTransactionStatus lastStatus = transaction.getStatus();
            PaymentTransactionStatus newStatus = paymentStatusConverter
                    .fromYooKassa(webhookBody.getData().getStatus());

            transaction.setStatus(newStatus);
            saverTransaction.save(transaction);

            if (lastStatus != PaymentTransactionStatus.SUCCEEDED
                    && newStatus == PaymentTransactionStatus.SUCCEEDED) {
                paymentFulfillmentService.fulfill(transaction);
            }

            if (lastStatus != PaymentTransactionStatus.CANCELED
                    && newStatus == PaymentTransactionStatus.CANCELED) {
                paymentFulfillmentService.cancel(transaction);
            }

            log.debug("Вебхук обработан: {}", webhookBody);
            return true;
        } catch (Exception e) {
            log.error("Не удалось обработать вебхук {}", webhookBody, e);
            return false;
        }
    }
}
