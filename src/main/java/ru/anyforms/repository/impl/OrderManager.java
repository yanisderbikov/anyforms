package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.model.Order;
import ru.anyforms.repository.GetterOrder;
import ru.anyforms.repository.GetterOrderByTracker;
import ru.anyforms.repository.OrderRepository;
import ru.anyforms.repository.SaverOrder;

import java.util.List;
import java.util.Optional;

import static ru.anyforms.service.task.OrderShipmentScheduler.READY_KEYWORDS;

@Log4j2
@Component
@AllArgsConstructor
class OrderManager implements GetterOrderByTracker, SaverOrder, GetterOrder {

    private OrderRepository orderRepository;

    @Transactional
    @Override
    public Optional<Order> getOptionalOrderByTracker(String tracker) {
        try {
            if (READY_KEYWORDS.contains(tracker)) {
                return Optional.empty();
            }
            return orderRepository.findOrderByTracker(tracker);
        } catch (Exception e) {
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void save(Order order) {
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Order> getEmptyDeliveryAndNonEmptyTracker() {
        try {
            return orderRepository.getEmptyDeliveryAndNonEmptyTracker();
        }catch (Exception e) {
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<Order> getNonDeliveredOrders() {
        try {
            return orderRepository.getNonDeliveredOrders();
        }catch (Exception e) {
            throw new RuntimeException("Database exception", e);
        }
    }
}
