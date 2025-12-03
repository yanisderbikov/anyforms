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

    Optional<Order> findOrderByTracker(String tracker);
}


