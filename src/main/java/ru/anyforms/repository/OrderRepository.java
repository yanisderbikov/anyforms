package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.Order;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByLeadId(Long leadId);

    @Query("SELECT o FROM Order o WHERE o.tracker IS NULL OR o.tracker = ''")
    List<Order> findOrdersWithoutTracker();

    List<Order> findOrdersByDeliveryStatus(String deliveryStatus);

    @Query("""
       SELECT o FROM Order o
       WHERE o.tracker IS NOT NULL
         AND o.tracker <> ''
         AND o.deliveryStatus IS NOT NULL
         AND o.deliveryStatus <> ''
         AND o.deliveryStatus <> :notDeliveryStatus
       """)
    List<Order> findOrdersFilledTrackerExceptDeliveryStatus(String notDeliveryStatus);


    Optional<Order> findOrderByTracker(String tracker);

    @Query("""
       SELECT o FROM Order o
       WHERE o.tracker IS NOT NULL
         AND o.tracker <> ''
         AND (
              o.deliveryStatus IS NULL
              OR o.deliveryStatus = ''
              OR o.deliveryStatus = 'CREATED'
         )
       """)
    List<Order> getEmptyOrCreatedDeliveryAndNonEmptyTracker();


    @Query("""
            SELECT o FROM Order o
            WHERE o.tracker IS NOT NULL
              AND o.tracker <> ''
              AND (o.deliveryStatus IS NULL OR o.deliveryStatus = '')
            """)
    List<Order> getEmptyDeliveryAndNonEmptyTracker();

    @Query("""
                SELECT o FROM Order o
                WHERE o.deliveryStatus <> 'DELIVERED'
            """)
    List<Order> getNonDeliveredOrders();
}


