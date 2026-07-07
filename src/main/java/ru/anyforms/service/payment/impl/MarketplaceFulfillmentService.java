package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.OrderService;
import ru.anyforms.service.task.TaskAdder;
import ru.anyforms.util.MoneyUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Фулфилмент заказа маркетплейса (order-first): заказ уже создан на чекауте со статусом
 * AWAITING_PAYMENT и позициями OrderItem (product_id = элемент каталога АМО из маппинга
 * товара). По вебхуку Юкассы:
 *   1) заказ переводится в PAID;
 *   2) ставится таска на письмо-чек (по позициям заказа с ценами на момент оплаты);
 *   3) в розничной воронке АМО заводится сделка сразу в статусе «Готов к отправке»
 *      с контактом (телефон при создании; ФИО и ПВЗ город/улица — кастомные поля контакта),
 *      бюджетом и датой оплаты;
 *   4) к сделке привязываются товары каталога АМО (по product_id позиций);
 *   5) заказ синкается из АМО — становится isRetail=true и едет по розничному флоу
 *      (цех ставит трекер, СДЭК-вебхуки двигают статусы).
 * Шаги 3-5 — best-effort: при недоступности АМО заказ остаётся PAID (виден в кастомных
 * списках), письмо уходит в любом случае.
 * При отмене платежа заказ помечается CANCELED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class MarketplaceFulfillmentService {

    private final OrderRepository orderRepository;
    private final AmoCrmGateway amoCrmGateway;
    private final OrderService orderService;
    private final TaskAdder taskAdder;

    @Value("${amocrm.retail.pipeline.id}")
    private Long retailPipelineId;

    @Value("${amocrm.status.ready.to.ship.id}")
    private Long readyToShipStatusId;

    @Value("${amocrm.products.catalog.id}")
    private Long productsCatalogId;

    @Transactional
    public void fulfill(PaymentTransaction transaction) {
        Order order = findOrder(transaction);
        if (order == null) {
            return;
        }

        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setPurchaseDate(LocalDateTime.now());
        orderRepository.save(order);

        // Письмо-чек собираем ДО пуша в АМО: синк пересоздаёт позиции без цен.
        List<OrderItem> items = new ArrayList<>(order.getItems());
        taskAdder.addTask(buildEmailPayload(transaction, order, items));

        pushToAmo(order, transaction, items);

        log.info("Маркетплейс: заказ #{} оплачен ({} позиций), письмо-чек на {}",
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

    /**
     * Заводит сделку в АМО и вводит заказ в розничный флоу. Best-effort: любой сбой
     * логируется, но не роняет фулфилмент (заказ остаётся PAID, письмо уже поставлено).
     */
    private void pushToAmo(Order order, PaymentTransaction transaction, List<OrderItem> items) {
        Long leadId;
        try {
            String name = order.getContactName() != null ? order.getContactName() : "Клиент";
            leadId = amoCrmGateway.createLead(
                    "Маркетплейс — " + name, name, order.getContactPhone(),
                    retailPipelineId, readyToShipStatusId);
            if (leadId == null) {
                log.error("Маркетплейс: АМО не вернула id сделки для заказа #{}", order.getId());
                return;
            }
            order.setLeadId(leadId);
            orderRepository.save(order);
        } catch (Exception e) {
            log.error("Маркетплейс: не удалось создать сделку в АМО для заказа #{}: {}",
                    order.getId(), e.getMessage());
            return;
        }

        fillLeadFields(leadId, transaction);
        fillContactFields(order, leadId);
        linkProducts(order, leadId, items);

        // Синк из АМО: подтягивает товары сделки → заказ становится isRetail=true
        // и попадает в розничный флоу (/orders/without-tracker → трекер → СДЭК).
        try {
            orderService.syncOrder(leadId);
        } catch (Exception e) {
            log.error("Маркетплейс: не удалось синкануть заказ #{} из АМО (lead {}): {}",
                    order.getId(), leadId, e.getMessage());
        }

        // Синк берёт дату покупки из амо-поля «Дата оплаты»; если оно не доехало —
        // не даём затереть дату оплаты в нашем заказе.
        if (order.getPurchaseDate() == null) {
            order.setPurchaseDate(LocalDateTime.now());
            orderRepository.save(order);
        }
    }

    /** Бюджет сделки и «Дата оплаты» (unix-секунды — так его парсит синк заказов). */
    private void fillLeadFields(Long leadId, PaymentTransaction transaction) {
        try {
            Long priceRub = transaction.getAmount() != null ? transaction.getAmount() / 100 : null;
            Map<Long, String> fields = Map.of(
                    AmoCrmFieldId.DATE_PAYMENT.getId(),
                    String.valueOf(java.time.Instant.now().getEpochSecond()));
            amoCrmGateway.updateLeadFields(leadId, priceRub, fields);
        } catch (Exception e) {
            log.error("Маркетплейс: не удалось заполнить поля сделки {}: {}", leadId, e.getMessage());
        }
    }

    /** ФИО и ПВЗ (город/улица) — кастомные поля контакта сделки. */
    private void fillContactFields(Order order, Long leadId) {
        try {
            Long contactId = amoCrmGateway.getContactIdFromLead(leadId);
            if (contactId == null) {
                log.warn("Маркетплейс: у сделки {} нет контакта — ФИО/ПВЗ не заполнены", leadId);
                return;
            }
            order.setContactId(contactId);
            orderRepository.save(order);

            Map<Long, String> fields = new HashMap<>();
            if (order.getContactName() != null) {
                fields.put(AmoCrmFieldId.FIO_CONTACT.getId(), order.getContactName());
            }
            if (order.getPvzSdekCity() != null) {
                fields.put(AmoCrmFieldId.PVZ_CITY_CONTACT.getId(), order.getPvzSdekCity());
            }
            if (order.getPvzSdekStreet() != null) {
                fields.put(AmoCrmFieldId.PVZ_STREET_CONTACT.getId(), order.getPvzSdekStreet());
            }
            if (!fields.isEmpty()) {
                amoCrmGateway.updateContactCustomField(contactId, fields);
            }
        } catch (Exception e) {
            log.error("Маркетплейс: не удалось заполнить контакт сделки {}: {}", leadId, e.getMessage());
        }
    }

    /** Привязывает товары каталога АМО к сделке; позиции без маппинга — примечанием в сделку. */
    private void linkProducts(Order order, Long leadId, List<OrderItem> items) {
        try {
            Map<Long, Integer> mapped = new LinkedHashMap<>();
            List<OrderItem> unmapped = new ArrayList<>();
            for (OrderItem item : items) {
                if (item.getProductId() != null) {
                    mapped.merge(item.getProductId(),
                            item.getQuantity() == null ? 1 : item.getQuantity(), Integer::sum);
                } else {
                    unmapped.add(item);
                }
            }

            if (!mapped.isEmpty()) {
                if (productsCatalogId == null || productsCatalogId <= 0) {
                    log.error("Маркетплейс: amocrm.products.catalog.id не настроен — товары к сделке {} не привязаны", leadId);
                } else if (!amoCrmGateway.linkCatalogElementsToLead(leadId, productsCatalogId, mapped)) {
                    log.error("Маркетплейс: не удалось привязать товары к сделке {}", leadId);
                }
            }

            if (!unmapped.isEmpty()) {
                String note = "Товары без маппинга на каталог АМО (добавьте вручную): "
                        + unmapped.stream()
                        .map(i -> i.getProductName() + " ×" + i.getQuantity())
                        .collect(Collectors.joining(", "));
                amoCrmGateway.addNoteToLead(leadId, note);
                log.warn("Маркетплейс: заказ #{} содержит позиции без amoProductId: {}", order.getId(), note);
            }
        } catch (Exception e) {
            log.error("Маркетплейс: ошибка привязки товаров к сделке {}: {}", leadId, e.getMessage());
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

    private MarketplaceOrderEmailPayload buildEmailPayload(PaymentTransaction transaction,
                                                           Order order,
                                                           List<OrderItem> items) {
        List<MarketplaceOrderEmailPayload.Item> emailItems = items.stream()
                .map(i -> MarketplaceOrderEmailPayload.Item.builder()
                        .name(i.getProductName())
                        .quantity(i.getQuantity())
                        .priceRub(i.getPriceKopecks() != null ? MoneyUtil.kopecksToString(i.getPriceKopecks()) : "")
                        .build())
                .collect(Collectors.toList());
        return MarketplaceOrderEmailPayload.builder()
                .to(transaction.getEmail())
                .orderPublicId(order.getPublicId())
                .customerName(order.getContactName())
                .pvzCity(order.getPvzSdekCity())
                .pvzStreet(order.getPvzSdekStreet())
                .totalRub(MoneyUtil.kopecksToString(transaction.getAmount()))
                .items(emailItems)
                .build();
    }
}
