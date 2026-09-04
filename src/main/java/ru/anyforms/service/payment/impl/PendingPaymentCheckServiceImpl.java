package ru.anyforms.service.payment.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.payment.tinkoff.TinkoffGetStateResponse;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.PendingPaymentCheckService;
import ru.anyforms.service.payment.TinkoffService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Т-Касса не присылает нотификацию, когда покупатель просто ушёл со страницы оплаты, поэтому
 * корзины розницы могут навсегда остаться в PENDING. Ссылка на оплату живёт
 * {@link TinkoffPaymentSupport#CART_LINK_TTL}; после этого банк переводит платёж
 * в DEADLINE_EXPIRED. Этот сервис раз в несколько минут спрашивает у банка (GetState) статус
 * зависших транзакций и применяет его тем же путём, что и вебхук: истёк или отклонён —
 * отмена и сделка «неудачная оплата» Ирине, оплачен без вебхука — заказ уходит в работу.
 * <p>
 * Если срок ссылки давно прошёл, а банк всё ещё держит платёж в промежуточном статусе
 * ({@link #FORCE_CANCEL_AFTER}), считаем корзину брошенной сами: оплатить по такой ссылке
 * уже нельзя. Транзакции старше {@link #LOOKBACK} не трогаем, чтобы при первом запуске
 * не завалить Ирину сделками по давно забытым корзинам.
 */
@Service
@Slf4j
class PendingPaymentCheckServiceImpl implements PendingPaymentCheckService {

    /** Запас после истечения ссылки на то, чтобы банк успел проставить статус; если не успел — дождёмся следующего тика. */
    static final Duration BANK_GRACE = Duration.ofMinutes(3);
    static final Duration FORCE_CANCEL_AFTER = TinkoffPaymentSupport.CART_LINK_TTL.plus(Duration.ofMinutes(30));
    static final Duration LOOKBACK = Duration.ofDays(1);

    private final GetterTransaction getterTransaction;
    private final TinkoffService tinkoffService;
    private final PaymentStatusConverter paymentStatusConverter;
    private final PaymentConfirmService paymentConfirmService;
    private final Clock clock;

    @Autowired
    PendingPaymentCheckServiceImpl(GetterTransaction getterTransaction,
                                   TinkoffService tinkoffService,
                                   PaymentStatusConverter paymentStatusConverter,
                                   PaymentConfirmService paymentConfirmService) {
        this(getterTransaction, tinkoffService, paymentStatusConverter, paymentConfirmService, Clock.systemUTC());
    }

    PendingPaymentCheckServiceImpl(GetterTransaction getterTransaction,
                                   TinkoffService tinkoffService,
                                   PaymentStatusConverter paymentStatusConverter,
                                   PaymentConfirmService paymentConfirmService,
                                   Clock clock) {
        this.getterTransaction = getterTransaction;
        this.tinkoffService = tinkoffService;
        this.paymentStatusConverter = paymentStatusConverter;
        this.paymentConfirmService = paymentConfirmService;
        this.clock = clock;
    }

    @Override
    public int check() {
        Instant now = clock.instant();
        List<PaymentTransaction> expired = getterTransaction.getByProviderStatusAndProductCodeCreatedBetween(
                PaymentProvider.TINKOFF, PaymentTransactionStatus.PENDING, PaymentProduct.CODE_MARKETPLACE_CART,
                now.minus(LOOKBACK), now.minus(TinkoffPaymentSupport.CART_LINK_TTL).minus(BANK_GRACE));

        int applied = 0;
        for (PaymentTransaction transaction : expired) {
            try {
                if (reconcile(transaction, now)) {
                    applied++;
                }
            } catch (Exception e) {
                log.error("Не удалось проверить зависший платёж {} ({})",
                        transaction.getId(), transaction.getExternalPaymentId(), e);
            }
        }
        if (!expired.isEmpty()) {
            log.info("Проверка зависших платежей розницы: кандидатов {}, применено статусов {}",
                    expired.size(), applied);
        }
        return applied;
    }

    /** true, если транзакции применили итоговый статус. */
    private boolean reconcile(PaymentTransaction transaction, Instant now) {
        TinkoffGetStateResponse state = tinkoffService.getState(transaction.getExternalPaymentId());
        PaymentTransactionStatus status = paymentStatusConverter.fromTinkoff(state.getStatus());

        if (status == PaymentTransactionStatus.FAILED) {
            log.warn("Зависший платёж {}: неизвестный статус Т-Кассы '{}' — пропускаем",
                    transaction.getId(), state.getStatus());
            return false;
        }
        if (status != PaymentTransactionStatus.PENDING) {
            log.info("Зависший платёж {}: банк отдал {} -> {}, применяем",
                    transaction.getId(), state.getStatus(), status);
            return paymentConfirmService.applyStatus(transaction.getExternalPaymentId(), status);
        }
        if (transaction.getCreatedAt().isBefore(now.minus(FORCE_CANCEL_AFTER))) {
            log.info("Зависший платёж {}: ссылка истекла ещё {}, а банк держит {} — считаем корзину брошенной",
                    transaction.getId(), transaction.getCreatedAt().plus(TinkoffPaymentSupport.CART_LINK_TTL),
                    state.getStatus());
            return paymentConfirmService.applyStatus(transaction.getExternalPaymentId(),
                    PaymentTransactionStatus.CANCELED);
        }
        log.debug("Зависший платёж {}: банк ещё держит {}, ждём следующего тика",
                transaction.getId(), state.getStatus());
        return false;
    }
}
