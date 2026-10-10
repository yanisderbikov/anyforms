package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffGetStateResponse;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.PromoReservationService;
import ru.anyforms.service.payment.TinkoffService;
import ru.anyforms.service.promo.PromoClient;
import ru.anyforms.service.promo.PromoClientChecker;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class PromoReservationServiceImpl implements PromoReservationService {

    private final GetterTransaction getterTransaction;
    private final TinkoffService tinkoffService;
    private final PaymentStatusConverter paymentStatusConverter;
    private final PaymentConfirmService paymentConfirmService;

    @Override
    public void releaseOwnReservations(PromoCode promo, PromoClient client) {
        if (promo.getMaxUses() == null || !client.hasDevice()) {
            return;
        }
        List<PaymentTransaction> pending = getterTransaction.getPendingByPromoCodeAndDevice(promo.getCode(),
                client.deviceId(), Instant.now().minus(PromoClientChecker.PENDING_RESERVATION));
        for (PaymentTransaction transaction : pending) {
            try {
                release(promo, transaction);
            } catch (Exception e) {
                log.warn("Резерв промокода {} платежом {} остаётся: {}", promo.getCode(), transaction.getId(), e.getMessage());
            }
        }
    }

    private void release(PromoCode promo, PaymentTransaction transaction) {
        if (transaction.getProvider() != PaymentProvider.TINKOFF) {
            log.info("Резерв промокода {} платежом {} ({}) остаётся до конца окна резервирования",
                    promo.getCode(), transaction.getId(), transaction.getProvider());
            return;
        }
        TinkoffGetStateResponse state = tinkoffService.getState(transaction.getExternalPaymentId());
        PaymentTransactionStatus bankStatus = paymentStatusConverter.fromTinkoff(state.getStatus());
        if (bankStatus == PaymentTransactionStatus.FAILED) {
            log.warn("Резерв промокода {} платежом {} остаётся: неизвестный статус Т-Кассы '{}'",
                    promo.getCode(), transaction.getId(), state.getStatus());
            return;
        }
        if (bankStatus != PaymentTransactionStatus.PENDING) {
            log.info("Платёж {} с промокодом {} в банке уже {} — применяем статус", transaction.getId(), promo.getCode(), bankStatus);
            paymentConfirmService.applyStatus(transaction.getExternalPaymentId(), bankStatus);
            return;
        }
        tinkoffService.cancel(TinkoffCancelRequest.builder()
                .paymentId(transaction.getExternalPaymentId())
                .build());
        paymentConfirmService.supersede(transaction.getExternalPaymentId());
        log.info("Неоплаченный платёж {} с промокодом {} отменён: клиент оформляет новый заказ",
                transaction.getId(), promo.getCode());
    }
}
