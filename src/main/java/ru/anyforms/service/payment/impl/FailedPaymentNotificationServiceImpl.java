package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.model.amo.AmoTaskResponsibleUser;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.payment.FailedPaymentNotificationService;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class FailedPaymentNotificationServiceImpl implements FailedPaymentNotificationService {

    static final String LEAD_NAME_PREFIX = "Неуспешная оплата - ";
    static final String TASK_TEXT = "не получилось оплатить продукт - связаться";
    /** Розница: в названии сделки перечисляем товары заказа, чтобы Ирина видела корзину без перехода. */
    static final String MARKETPLACE_LEAD_NAME_PREFIX = "Неудачная оплата Розницы - ";
    static final String MARKETPLACE_TASK_TEXT = "неудачная оплата Розницы - связаться";
    static final int TASK_DEADLINE_MINUTES = 24 * 60;

    static final long EDUCATION_PIPELINE_ID = 10863606L;
    static final long EDUCATION_FAILED_STATUS_ID = 85480278L;
    static final long MARKETPLACE_FAILED_PIPELINE_ID = 10557858L;
    static final long MARKETPLACE_FAILED_STATUS_ID = 83287002L;

    private final AmoCrmGateway amoCrmGateway;
    private final AmoContactFinder amoContactFinder;
    private final OrderRepository orderRepository;

    @Override
    public void notify(PaymentTransaction transaction) {
        PipelineTarget target = resolveTarget(transaction.getProductCode());
        if (target == null) {
            log.info("Неуспешная оплата: продукт {} в АМО не ведём (транзакция {})",
                    transaction.getProductCode(), transaction.getId());
            return;
        }

        Order order = isMarketplace(transaction) ? findOrder(transaction) : null;
        String contactName = firstNonBlank(transaction.getContactName(),
                order != null ? order.getContactName() : null);
        String phone = firstNonBlank(transaction.getContactPhone(),
                order != null ? order.getContactPhone() : null);

        Long existingContactId = amoContactFinder.findByEmailOrPhone(transaction.getEmail(), phone);
        String taskText = taskText(transaction);

        Long existingLeadId = findExistingFailedLead(existingContactId, target);
        if (existingLeadId != null) {
            if (amoCrmGateway.hasIncompleteTask(existingLeadId)) {
                log.info("Неуспешная оплата: по сделке {} уже есть невыполненная задача — ничего не создаём (транзакция {})",
                        existingLeadId, transaction.getId());
                return;
            }
            amoCrmGateway.setNewTask(AmoTaskResponsibleUser.IRINA.getResponsibleUserId(),
                    AmoTaskId.LOST_MESSAGE.getTaskId(), taskText, existingLeadId, TASK_DEADLINE_MINUTES);
            log.info("Неуспешная оплата: сделка {} уже есть — добавили только задачу (транзакция {})",
                    existingLeadId, transaction.getId());
            return;
        }

        Long leadId = amoCrmGateway.createLead(leadName(transaction, order),
                contactName != null ? contactName : "Клиент", phone, transaction.getEmail(),
                target.pipelineId(), target.statusId(),
                AmoTaskResponsibleUser.IRINA.getResponsibleUserId());
        if (leadId == null) {
            log.info("Неуспешная оплата: АМО выключена — сделка по транзакции {} не создана",
                    transaction.getId());
            return;
        }
        if (existingContactId == null) {
            amoContactFinder.fillContactFio(leadId, contactName);
        }

        amoCrmGateway.setNewTask(AmoTaskResponsibleUser.IRINA.getResponsibleUserId(),
                AmoTaskId.LOST_MESSAGE.getTaskId(), taskText, leadId, TASK_DEADLINE_MINUTES);
    }

    private static boolean isMarketplace(PaymentTransaction transaction) {
        return PaymentProduct.CODE_MARKETPLACE_CART.equals(transaction.getProductCode());
    }

    private String taskText(PaymentTransaction transaction) {
        return isMarketplace(transaction) ? MARKETPLACE_TASK_TEXT : TASK_TEXT;
    }

    private String leadName(PaymentTransaction transaction, Order order) {
        if (isMarketplace(transaction)) {
            return MARKETPLACE_LEAD_NAME_PREFIX + orderItemsSummary(order);
        }
        return LEAD_NAME_PREFIX + transaction.getProductCode();
    }

    private Order findOrder(PaymentTransaction transaction) {
        Order order = transaction.getOrderId() == null
                ? null
                : orderRepository.findById(transaction.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Неуспешная оплата: заказ {} по транзакции {} не найден — без товаров в названии и контактов заказа",
                    transaction.getOrderId(), transaction.getId());
        }
        return order;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second != null && !second.isBlank() ? second : null);
    }

    /** «Свеча Луна, Подсвечник ×2»; если заказ не нашли или он пуст — его номер, чтобы сделка всё равно создалась. */
    private String orderItemsSummary(Order order) {
        if (order == null) {
            return "заказ не найден";
        }
        String items = order.getItems().stream()
                .map(FailedPaymentNotificationServiceImpl::itemLabel)
                .collect(Collectors.joining(", "));
        return items.isBlank() ? "заказ " + order.getPublicId() : items;
    }

    private static String itemLabel(OrderItem item) {
        String name = item.getProductName() != null ? item.getProductName() : "товар";
        return item.getQuantity() != null && item.getQuantity() > 1 ? name + " ×" + item.getQuantity() : name;
    }

    private Long findExistingFailedLead(Long contactId, PipelineTarget target) {
        if (contactId == null) {
            return null;
        }
        for (Long leadId : amoCrmGateway.getLeadIdsByContact(contactId)) {
            AmoLead lead = amoCrmGateway.getLead(leadId);
            if (lead != null && target.pipelineId().equals(lead.getPipelineId())) {
                return leadId;
            }
        }
        return null;
    }

    private PipelineTarget resolveTarget(String productCode) {
        if (productCode == null) {
            return null;
        }
        return switch (productCode) {
            case PaymentProduct.CODE_MARKETPLACE_CART ->
                    new PipelineTarget(MARKETPLACE_FAILED_PIPELINE_ID, MARKETPLACE_FAILED_STATUS_ID);
            case PaymentProduct.CODE_GUIDE, PaymentProduct.CODE_COURSE, PaymentProduct.CODE_COURSE_PERSONAL ->
                    new PipelineTarget(EDUCATION_PIPELINE_ID, EDUCATION_FAILED_STATUS_ID);
            default -> null;
        };
    }

    private record PipelineTarget(Long pipelineId, Long statusId) {
    }
}
