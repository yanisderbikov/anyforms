package ru.anyforms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.model.Order;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.repository.CustomProductItemRepository;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.repository.TelegramNotificationRepository;
import ru.anyforms.service.RetailOrderDeleter;

import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class RetailOrderDeleterImpl implements RetailOrderDeleter {

    private final OrderRepository orderRepository;
    private final CustomProductItemRepository customProductItemRepository;
    private final TelegramNotificationRepository telegramNotificationRepository;
    private final GetterTransaction getterTransaction;
    private final SaverTransaction saverTransaction;

    @Override
    @Transactional
    public void delete(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден: " + orderId));
        if (!order.isRetail()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Удалять можно только розничные заказы");
        }
        if (customProductItemRepository.countByOrderId(orderId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "У заказа есть позиции под заказ — удалите их через раздел «Под заказ»");
        }

        // Платежи не удаляем (история денег), только отвязываем от заказа:
        // иначе FK payment_transaction.order_id не даст удалить строку orders.
        List<PaymentTransaction> transactions = getterTransaction.getByOrderId(orderId);
        for (PaymentTransaction transaction : transactions) {
            transaction.setOrderId(null);
            saverTransaction.save(transaction);
        }

        int notifications = telegramNotificationRepository.deleteByOrderIdIn(List.of(orderId));

        // order_items уходят каскадом (cascade = ALL, orphanRemoval).
        orderRepository.delete(order);

        log.info("Розничный заказ удалён супер-админом: id={}, publicId={}, leadId={}, items={}, detachedPayments={}, tgNotifications={}",
                orderId, order.getPublicId(), order.getLeadId(), order.getItems().size(), transactions.size(), notifications);
    }
}
