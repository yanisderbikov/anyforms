package ru.anyforms.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.CustomProductStatus;

import java.util.List;

public interface CustomProductItemRepository extends JpaRepository<CustomProductItem, Long> {

    /** Кастомные позиции сделки по нашему order.id. */
    List<CustomProductItem> findByOrderIdOrderByIdAsc(Long orderId);

    long countByOrderId(Long orderId);

    /** Все позиции, кроме указанного статуса (скрываем SENT). */
    List<CustomProductItem> findByStatusNot(CustomProductStatus status, Sort sort);

    /** Позиции заказа, кроме указанного статуса (скрываем SENT). */
    List<CustomProductItem> findByOrderIdAndStatusNot(Long orderId, CustomProductStatus status, Sort sort);

    /** Позиции по статусу (для «к отправке»). */
    List<CustomProductItem> findByStatus(CustomProductStatus status);

    /** Позиции заказа в статусе (для отгрузки). */
    List<CustomProductItem> findByOrderIdAndStatus(Long orderId, CustomProductStatus status);
}
