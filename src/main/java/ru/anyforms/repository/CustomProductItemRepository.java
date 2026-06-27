package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.anyforms.model.CustomProductItem;

import java.util.List;

public interface CustomProductItemRepository extends JpaRepository<CustomProductItem, Long> {

    /** Кастомные позиции сделки по нашему order.id. */
    List<CustomProductItem> findByOrderIdOrderByIdAsc(Long orderId);

    long countByOrderId(Long orderId);
}
