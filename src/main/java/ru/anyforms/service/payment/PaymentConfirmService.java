package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.YooKassaWebhookBody;

public interface PaymentConfirmService {
    boolean confirm(YooKassaWebhookBody webhookBody);

    boolean confirmTinkoff(String rawNotificationBody);
}
