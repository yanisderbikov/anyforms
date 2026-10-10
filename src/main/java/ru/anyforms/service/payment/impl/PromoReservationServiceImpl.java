package ru.anyforms.service.payment.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffGetStateResponse;
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
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
class PromoReservationServiceImpl implements PromoReservationService {

    static final Set<String> CANCELLABLE_BANK_STATUSES = Set.of("NEW", "FORM_SHOWED");

    private final GetterTransaction getterTransaction;
    private final TinkoffService tinkoffService;
    private final PaymentStatusConverter paymentStatusConverter;
    private final PaymentConfirmService paymentConfirmService;
    private final TransactionTemplate separateTransaction;

    PromoReservationServiceImpl(GetterTransaction getterTransaction,
                                TinkoffService tinkoffService,
                                PaymentStatusConverter paymentStatusConverter,
                                PaymentConfirmService paymentConfirmService,
                                PlatformTransactionManager transactionManager) {
        this.getterTransaction = getterTransaction;
        this.tinkoffService = tinkoffService;
        this.paymentStatusConverter = paymentStatusConverter;
        this.paymentConfirmService = paymentConfirmService;
        this.separateTransaction = new TransactionTemplate(transactionManager);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean releaseOwnReservations(PromoCode promo, PromoClient client) {
        if (promo.getMaxUses() == null || !client.hasDevice()) {
            return false;
        }
        List<String> paymentIds = getterTransaction.getPendingTinkoffPaymentIds(promo.getCode(), client.deviceId(),
                Instant.now().minus(PromoClientChecker.PENDING_RESERVATION));
        boolean released = false;
        for (String paymentId : paymentIds) {
            try {
                released |= release(promo, paymentId);
            } catch (Exception e) {
                log.warn("Резерв промокода {} платежом {} остаётся: {}", promo.getId(), paymentId, e.getMessage());
            }
        }
        return released;
    }

    private boolean release(PromoCode promo, String paymentId) {
        TinkoffGetStateResponse state = tinkoffService.getState(paymentId);
        String bankStatus = state.getStatus() == null ? "" : state.getStatus().toUpperCase(Locale.ROOT);
        if (!CANCELLABLE_BANK_STATUSES.contains(bankStatus)) {
            log.info("Резерв промокода {} платежом {} остаётся: в банке статус {}", promo.getId(), paymentId, bankStatus);
            return false;
        }
        TinkoffCancelResponse cancel = tinkoffService.cancel(TinkoffCancelRequest.builder()
                .paymentId(paymentId)
                .build());
        PaymentTransactionStatus afterCancel = paymentStatusConverter.fromTinkoff(cancel.getStatus());
        if (afterCancel != PaymentTransactionStatus.CANCELED) {
            log.warn("Платёж {} после отмены в банке в статусе {}, итог придёт нотификацией", paymentId, cancel.getStatus());
            return false;
        }
        Boolean superseded = separateTransaction.execute(status -> paymentConfirmService.supersede(paymentId));
        log.info("Неоплаченный платёж {} с промокодом {} отменён: клиент оформляет новый заказ", paymentId, promo.getId());
        return Boolean.TRUE.equals(superseded);
    }
}
