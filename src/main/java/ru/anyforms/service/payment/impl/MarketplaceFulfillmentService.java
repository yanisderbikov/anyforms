package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.task.TaskAdder;
import ru.anyforms.util.MoneyUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Фулфилмент заказа маркетплейса (order-first): заказ уже создан на чекауте со статусом
 * AWAITING_PAYMENT — по вебхуку Юкассы переводим его в PAID, заводим лид в AmoCRM и
 * ставим таску на письмо-чек. При отмене платежа заказ помечается CANCELED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class MarketplaceFulfillmentService {

    private final OrderRepository orderRepository;
    private final CustomProductItemRepository customProductItemRepository;
    private final AmoCrmGateway amoCrmGateway;
    private final TaskAdder taskAdder;

    @Transactional
    public void fulfill(PaymentTransaction transaction) {
        Order order = findOrder(transaction);
        if (order == null) {
            return;
        }

        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setPurchaseDate(LocalDateTime.now());
        orderRepository.save(order);

        List<CustomProductItem> items = customProductItemRepository
                .findByOrderIdOrderByIdAsc(order.getId());

        createAmoLead(order, transaction);

        taskAdder.addTask(buildEmailPayload(transaction, order, items));
        log.info("Маркетплейс: заказ #{} оплачен ({} позиций), поставлена таска на письмо-чек на {}",
                order.getId(), items.size(), transaction.getEmail());
    }

    @Transactional
    public void cancel(PaymentTransaction transaction) {
        Order order = findOrder(transaction);
        if (order == null) {
            return;
        }
        // Не отменяем уже оплаченный заказ задним числом.
        if (order.getPaymentStatus() == OrderPaymentStatus.AWAITING_PAYMENT) {
            order.setPaymentStatus(OrderPaymentStatus.CANCELED);
            orderRepository.save(order);
            log.info("Маркетплейс: платёж отменён, заказ #{} помечен CANCELED", order.getId());
        }
    }

    private Order findOrder(PaymentTransaction transaction) {
        if (transaction.getOrderId() == null) {
            log.error("Маркетплейс: у транзакции {} нет orderId", transaction.getId());
            return null;
        }
        return orderRepository.findById(transaction.getOrderId())
                .orElseGet(() -> {
                    log.error("Маркетплейс: заказ #{} не найден (транзакция {})",
                            transaction.getOrderId(), transaction.getId());
                    return null;
                });
    }

    /** Лид в AmoCRM — best-effort: не роняем фулфилмент, если CRM недоступна (лид можно завести позже). */
    private void createAmoLead(Order order, PaymentTransaction transaction) {
        try {
            String name = order.getContactName() != null ? order.getContactName() : "Клиент";
            Long leadId = amoCrmGateway.createLandingLead("Маркетплейс — " + name, name, order.getContactPhone());
            if (leadId != null) {
                order.setLeadId(leadId);
                orderRepository.save(order);
            }
        } catch (Exception e) {
            log.error("Маркетплейс: не удалось создать лид в AmoCRM для заказа #{}: {}",
                    order.getId(), e.getMessage());
        }
    }

    private MarketplaceOrderEmailPayload buildEmailPayload(PaymentTransaction transaction,
                                                           Order order,
                                                           List<CustomProductItem> items) {
        List<MarketplaceOrderEmailPayload.Item> emailItems = items.stream()
                .map(i -> MarketplaceOrderEmailPayload.Item.builder()
                        .name(i.getProductName())
                        .quantity(i.getQuantity())
                        .priceRub(i.getPriceKopecks() != null ? MoneyUtil.kopecksToString(i.getPriceKopecks()) : "")
                        .build())
                .collect(Collectors.toList());
        return MarketplaceOrderEmailPayload.builder()
                .to(transaction.getEmail())
                .customerName(order.getContactName())
                .pvzCity(order.getPvzSdekCity())
                .pvzStreet(order.getPvzSdekStreet())
                .totalRub(MoneyUtil.kopecksToString(transaction.getAmount()))
                .items(emailItems)
                .build();
    }
}
