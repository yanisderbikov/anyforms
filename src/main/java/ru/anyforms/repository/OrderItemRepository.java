package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.OrderItem;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    @Modifying
    @Query("""
        DELETE FROM OrderItem oi
        WHERE oi.order.leadId IN :leadIds
    """)
    int deleteByOrderLeadIds(List<Long> leadIds);
}
