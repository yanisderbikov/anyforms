package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.TelegramNotification;

import java.util.List;

@Repository
public interface TelegramNotificationRepository extends JpaRepository<TelegramNotification, Long> {

    boolean existsByOrderId(Long orderId);

    List<TelegramNotification> findAllByOrderByIdAsc();

    int deleteByOrderIdIn(List<Long> orderIds);
}
