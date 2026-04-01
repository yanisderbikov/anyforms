package ru.anyforms.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.model.Order;
import ru.anyforms.repository.*;

import java.util.List;
import java.util.Optional;

import static ru.anyforms.util.TrackerCustomFields.READY_KEYWORDS;


@Log4j2
@Component
@AllArgsConstructor
class OrderManager implements GetterOrderByTracker, SaverOrder, GetterOrder, OrderDeleter {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;

    @Transactional
    @Override
    public Optional<Order> getOptionalOrderByTracker(String tracker) {
        try {
            String normalizedTracker = tracker == null ? null : tracker.trim();

            if (normalizedTracker == null
                    || normalizedTracker.isBlank()
                    || normalizedTracker.chars().noneMatch(Character::isDigit)
                    || READY_KEYWORDS.contains(normalizedTracker)) {

                log.info("impossible update this tracker, returning Optional Empty : {}", tracker);
                return Optional.empty();
            }

            return orderRepository.findOrderByTracker(normalizedTracker);
        } catch (Exception e) {
            log.error("fail find order with tracker {}", tracker, e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void save(Order order) {
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            log.error("fail save order {}", order, e);
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

    @Transactional
    @Override
    public void deleteByLeadId(List<Long> leadIds) {
        try {
            if (leadIds == null || leadIds.isEmpty()) {
                return;
            }

            orderItemRepository.deleteByOrderLeadIds(leadIds);
            int result = orderRepository.deleteByLeadIds(leadIds);
            log.info("deleted {} orders", result);
        }catch (Exception e) {
            throw new RuntimeException("Database exception", e);
        }
    }
}
