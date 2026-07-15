package ru.anyforms.service.payment.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.YooKassaWebhookBody;
import ru.anyforms.dto.payment.tinkoff.TinkoffNotification;
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
    private final TinkoffTokenService tinkoffTokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean confirm(YooKassaWebhookBody webhookBody) {
        try {
            PaymentTransactionStatus newStatus = paymentStatusConverter
                    .fromYooKassa(webhookBody.getData().getStatus());
            return applyStatus(String.valueOf(webhookBody.getData().getId()), newStatus);
        } catch (Exception e) {
            log.error("Не удалось обработать вебхук {}", webhookBody, e);
            return false;
        }
    }

    @Override
    public boolean confirmTinkoff(String rawNotificationBody) {
        try {
            JsonNode root = objectMapper.readTree(rawNotificationBody);
            if (!tinkoffTokenService.verify(root)) {
                log.error("Нотификация Т-Кассы с невалидным Token: {}", rawNotificationBody);
                return false;
            }
            TinkoffNotification notification = objectMapper.treeToValue(root, TinkoffNotification.class);
            if (notification.getPaymentId() == null) {
                log.error("Нотификация Т-Кассы без PaymentId: {}", rawNotificationBody);
                return false;
            }
            PaymentTransactionStatus newStatus = paymentStatusConverter
                    .fromTinkoff(notification.getStatus());
            return applyStatus(String.valueOf(notification.getPaymentId()), newStatus);
        } catch (Exception e) {
            log.error("Не удалось обработать нотификацию Т-Кассы {}", rawNotificationBody, e);
            return false;
        }
    }

    private boolean applyStatus(String externalPaymentId, PaymentTransactionStatus newStatus) {
        PaymentTransaction transaction = getterTransaction
                .getByExternalPaymentId(externalPaymentId)
                .orElseThrow(() -> new RuntimeException(
                        "Транзакция не найдена по external id: " + externalPaymentId));

        PaymentTransactionStatus lastStatus = transaction.getStatus();

        if (newStatus == PaymentTransactionStatus.PENDING
                && (lastStatus == PaymentTransactionStatus.SUCCEEDED
                || lastStatus == PaymentTransactionStatus.CANCELED
                || lastStatus == PaymentTransactionStatus.REFUNDED)) {
            log.debug("Игнорируем откат статуса {} -> {} для транзакции {}",
                    lastStatus, newStatus, transaction.getId());
            return true;
        }

        // Возврат — терминальный статус: запоздавшие SUCCEEDED/CANCELED его не перебивают.
        if (lastStatus == PaymentTransactionStatus.REFUNDED
                && newStatus != PaymentTransactionStatus.REFUNDED) {
            log.debug("Игнорируем статус {} после возврата для транзакции {}",
                    newStatus, transaction.getId());
            return true;
        }

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

        if (lastStatus != PaymentTransactionStatus.REFUNDED
                && newStatus == PaymentTransactionStatus.REFUNDED) {
            paymentFulfillmentService.refund(transaction);
        }

        log.debug("Вебхук обработан: транзакция {} перешла {} -> {}",
                transaction.getId(), lastStatus, newStatus);
        return true;
    }
}
