package ru.anyforms.service.payment.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderItem;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.model.amo.AmoTaskResponsibleUser;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.service.payment.FailedPaymentNotificationService;
import ru.anyforms.util.MoneyUtil;

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
    static final String MARKETPLACE_NOTE_PREFIX = "Не получилось оплатить";
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

        FoundLead existing = findExistingFailedLead(existingContactId, target);
        if (existing != null) {
            Long existingLeadId = existing.id();
            ensureLeadPlacement(existing, target, transaction);
            if (amoCrmGateway.hasIncompleteTask(existingLeadId)) {
                log.info("Неуспешная оплата: по сделке {} уже есть невыполненная задача — новую не ставим (транзакция {})",
                        existingLeadId, transaction.getId());
            } else {
                amoCrmGateway.setNewTask(AmoTaskResponsibleUser.IRINA.getResponsibleUserId(),
                        AmoTaskId.LOST_MESSAGE.getTaskId(), taskText, existingLeadId, TASK_DEADLINE_MINUTES);
                log.info("Неуспешная оплата: сделка {} уже есть — добавили только задачу (транзакция {})",
                        existingLeadId, transaction.getId());
            }
            addFailedItemsNote(existingLeadId, transaction, order);
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
        addFailedItemsNote(leadId, transaction, order);
    }

    /**
     * Примечание к сделке розницы со списком товаров, которые не получилось купить.
     * Добавляется всегда: и к новой сделке, и к уже существующей, даже если задачу не ставили —
     * иначе по старой сделке не видно, что именно клиент пытался купить в этот раз.
     */
    private void addFailedItemsNote(Long leadId, PaymentTransaction transaction, Order order) {
        if (!isMarketplace(transaction)) {
            return;
        }
        if (!amoCrmGateway.addNoteToLead(leadId, failedItemsNote(transaction, order))) {
            log.warn("Неуспешная оплата: не удалось добавить примечание с товарами к сделке {} (транзакция {})",
                    leadId, transaction.getId());
        }
    }

    static String failedItemsNote(PaymentTransaction transaction, Order order) {
        StringBuilder note = new StringBuilder(MARKETPLACE_NOTE_PREFIX);
        if (order != null && order.getPublicId() != null) {
            note.append(" заказ ").append(order.getPublicId());
        }
        if (transaction.getAmount() != null) {
            note.append(" на ").append(MoneyUtil.formatRubles(transaction.getAmount()));
        }
        note.append(":");
        if (order == null || order.getItems().isEmpty()) {
            note.append("\n— состав заказа не найден");
            return note.toString();
        }
        for (OrderItem item : order.getItems()) {
            note.append("\n— ")
                    .append(item.getProductName() != null ? item.getProductName() : "товар")
                    .append(" × ")
                    .append(item.getQuantity() != null ? item.getQuantity() : 1);
        }
        return note.toString();
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

    /**
     * Сделка клиента в целевой воронке: сначала та, что уже в статусе неудачной оплаты, потом
     * любая открытая, и только потом закрытая («Реализовано»/«Не реализовано») — её возвращаем
     * в работу переводом статуса, а не заводим новую.
     */
    private FoundLead findExistingFailedLead(Long contactId, PipelineTarget target) {
        if (contactId == null) {
            return null;
        }
        FoundLead open = null;
        FoundLead closed = null;
        for (Long leadId : amoCrmGateway.getLeadIdsByContact(contactId)) {
            AmoLead lead = amoCrmGateway.getLead(leadId);
            if (lead == null || !target.pipelineId().equals(lead.getPipelineId())) {
                continue;
            }
            FoundLead found = new FoundLead(leadId, lead.getStatusId(), lead.getResponsibleUserId());
            if (target.statusId().equals(lead.getStatusId())) {
                return found;
            }
            if (isClosed(lead)) {
                closed = closed == null ? found : closed;
            } else {
                open = open == null ? found : open;
            }
        }
        return open != null ? open : closed;
    }

    private static boolean isClosed(AmoLead lead) {
        return AmoLeadStatus.REALIZED.getStatusId().equals(lead.getStatusId())
                || AmoLeadStatus.NOT_REALIZED.getStatusId().equals(lead.getStatusId());
    }

    /**
     * Старая сделка могла уехать в другой статус, закрыться или стоять на ком угодно, а неудачную
     * оплату должна дожимать Ирина из статуса неудачной оплаты: переводим статус и ответственного.
     */
    private void ensureLeadPlacement(FoundLead lead, PipelineTarget target, PaymentTransaction transaction) {
        Long manager = AmoTaskResponsibleUser.IRINA.getResponsibleUserId();
        boolean statusOk = target.statusId().equals(lead.statusId());
        boolean responsibleOk = manager.equals(lead.responsibleUserId());
        if (statusOk && responsibleOk) {
            return;
        }
        boolean updated = statusOk
                ? amoCrmGateway.updateLeadResponsible(lead.id(), manager)
                : amoCrmGateway.updateLeadStatus(lead.id(), target.statusId(), target.pipelineId(), manager);
        if (updated) {
            log.info("Неуспешная оплата: сделка {} была в статусе {} на {} — перевели в {} на Ирину (транзакция {})",
                    lead.id(), lead.statusId(), lead.responsibleUserId(), target.statusId(), transaction.getId());
        } else {
            log.warn("Неуспешная оплата: не удалось перевести сделку {} в статус {} на Ирину (транзакция {})",
                    lead.id(), target.statusId(), transaction.getId());
        }
    }

    private record FoundLead(Long id, Long statusId, Long responsibleUserId) {
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
