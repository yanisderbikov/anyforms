package ru.anyforms.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.anyforms.model.CustomProductItem;
import ru.anyforms.model.CustomProductStatus;

import java.util.Collection;
import java.util.List;

public interface CustomProductItemRepository extends JpaRepository<CustomProductItem, Long> {

    /** Уникальные непустые значения «кто моделирует» (для select с автодобавлением). */
    @Query("SELECT DISTINCT i.modeler FROM CustomProductItem i WHERE i.modeler IS NOT NULL AND i.modeler <> '' ORDER BY i.modeler")
    List<String> findDistinctModelers();

    /** Кастомные позиции сделки по нашему order.id. */
    List<CustomProductItem> findByOrderIdOrderByIdAsc(Long orderId);

    long countByOrderId(Long orderId);

    /**
     * Все позиции, кроме указанных статусов (скрываем DELIVERING и COMPLETED).
     * Позиции неоплаченных/отменённых заказов маркетплейса в работу не попадают.
     */
    @Query("""
       SELECT i FROM CustomProductItem i
       WHERE i.status NOT IN :statuses
         AND i.order.paymentStatus NOT IN (ru.anyforms.model.OrderPaymentStatus.AWAITING_PAYMENT,
                                           ru.anyforms.model.OrderPaymentStatus.CANCELED)
    """)
    List<CustomProductItem> findByStatusNotIn(Collection<CustomProductStatus> statuses, Sort sort);

    /** Позиции заказа, кроме указанных статусов (скрываем DELIVERING и COMPLETED). */
    List<CustomProductItem> findByOrderIdAndStatusNotIn(Long orderId, Collection<CustomProductStatus> statuses, Sort sort);

    /**
     * Позиции по статусу (для «к отправке»).
     * Позиции неоплаченных/отменённых заказов маркетплейса не показываем.
     */
    @Query("""
       SELECT i FROM CustomProductItem i
       WHERE i.status = :status
         AND i.order.paymentStatus NOT IN (ru.anyforms.model.OrderPaymentStatus.AWAITING_PAYMENT,
                                           ru.anyforms.model.OrderPaymentStatus.CANCELED)
    """)
    List<CustomProductItem> findByStatus(CustomProductStatus status);

    /** Позиции заказа в статусе (для отгрузки). */
    List<CustomProductItem> findByOrderIdAndStatus(Long orderId, CustomProductStatus status);
}
