package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.Order;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionItem;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.task.TaskAdder;
import ru.anyforms.util.MoneyUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Фулфилмент оплаченного заказа маркетплейса: из снапшота транзакции создаёт локальный
 * {@link Order} (виден в /orders) с кастомными позициями, заводит лид в AmoCRM и ставит
 * таску на письмо-чек. Вызывается из {@code PaymentFulfillmentServiceImpl} при переходе
 * транзакции MARKETPLACE_CART в статус SUCCEEDED.
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
        List<PaymentTransactionItem> items = transaction.getItems();

        Order order = new Order();
        order.setRetail(false);
        order.setContactName(transaction.getCustomerName());
        order.setContactPhone(transaction.getCustomerPhone());
        order.setPvzSdekCity(transaction.getPvzCity());
        order.setPvzSdekStreet(transaction.getPvzStreet());
        order.setPurchaseDate(LocalDateTime.now());
        order.setComment("Заказ с сайта, оплачен онлайн. " + itemsSummary(items)
                + ". ПВЗ СДЭК: " + pvzSummary(transaction));
        Order saved = orderRepository.save(order);

        for (PaymentTransactionItem item : items) {
            CustomProductItem custom = new CustomProductItem();
            custom.setOrder(saved);
            custom.setProductName(item.getProductName());
            custom.setQuantity(item.getQuantity());
            customProductItemRepository.save(custom);
        }

        createAmoLead(saved, transaction, items);

        taskAdder.addTask(buildEmailPayload(transaction, items));
        log.info("Маркетплейс: создан заказ #{} ({} позиций), поставлена таска на письмо-чек на {}",
                saved.getId(), items.size(), transaction.getEmail());
    }

    /** Лид в AmoCRM — best-effort: не роняем фулфилмент, если CRM недоступна (лид можно завести позже). */
    private void createAmoLead(Order order, PaymentTransaction transaction, List<PaymentTransactionItem> items) {
        try {
            String name = transaction.getCustomerName() != null ? transaction.getCustomerName() : "Клиент";
            String leadName = "Маркетплейс — " + name;
            Long leadId = amoCrmGateway.createLandingLead(leadName, name, transaction.getCustomerPhone());
            if (leadId != null) {
                order.setLeadId(leadId);
                order.setComment("Состав: " + itemsSummary(items));
                orderRepository.save(order);
            }
        } catch (Exception e) {
            log.error("Маркетплейс: не удалось создать лид в AmoCRM для заказа #{}: {}",
                    order.getId(), e.getMessage());
        }
    }

    private MarketplaceOrderEmailPayload buildEmailPayload(PaymentTransaction transaction, List<PaymentTransactionItem> items) {
        List<MarketplaceOrderEmailPayload.Item> emailItems = items.stream()
                .map(i -> MarketplaceOrderEmailPayload.Item.builder()
                        .name(i.getProductName())
                        .quantity(i.getQuantity())
                        .priceRub(MoneyUtil.kopecksToString(i.getPriceKopecks()))
                        .build())
                .collect(Collectors.toList());
        return MarketplaceOrderEmailPayload.builder()
                .to(transaction.getEmail())
                .customerName(transaction.getCustomerName())
                .pvzCity(transaction.getPvzCity())
                .pvzStreet(transaction.getPvzStreet())
                .totalRub(MoneyUtil.kopecksToString(transaction.getAmount()))
                .items(emailItems)
                .build();
    }

    private String itemsSummary(List<PaymentTransactionItem> items) {
        return items.stream()
                .map(i -> i.getProductName() + " ×" + i.getQuantity())
                .collect(Collectors.joining(", "));
    }

    private String pvzSummary(PaymentTransaction transaction) {
        StringBuilder sb = new StringBuilder();
        if (transaction.getPvzCity() != null) {
            sb.append(transaction.getPvzCity());
        }
        if (transaction.getPvzStreet() != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(transaction.getPvzStreet());
        }
        if (transaction.getPvzCode() != null) {
            sb.append(" [").append(transaction.getPvzCode()).append("]");
        }
        return sb.toString();
    }
}
