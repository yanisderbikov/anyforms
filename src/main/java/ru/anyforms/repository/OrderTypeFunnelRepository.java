package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.model.salesbot.OrderTypeFunnel;

import java.util.Optional;

@Repository
public interface OrderTypeFunnelRepository extends JpaRepository<OrderTypeFunnel, Long> {

    Optional<OrderTypeFunnel> findByType(OrderType type);
}
