package ru.anyforms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.anyforms.model.Order;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByLeadId(Long leadId);

    boolean existsByPublicId(String publicId);

    Optional<Order> findByPublicId(String publicId);

    /** Под-заказные сделки (без товаров из amo-каталога). */
    List<Order> findByIsRetailFalseOrderByCreatedAtDesc();

    /**
     * Под-заказные сделки для работы цеха: без неоплаченных и отменённых заказов
     * маркетплейса (брошенные корзины в работу не попадают).
     */
    @Query("""
       SELECT o FROM Order o
       WHERE o.isRetail = FALSE
         AND o.paymentStatus NOT IN (ru.anyforms.model.OrderPaymentStatus.AWAITING_PAYMENT,
                                     ru.anyforms.model.OrderPaymentStatus.CANCELED,
                                     ru.anyforms.model.OrderPaymentStatus.REFUNDED)
       ORDER BY o.createdAt DESC
    """)
    List<Order> findWorkableCustomOrders();

    /** Поиск по ФИО/телефону (для предзаполнения нового заказа). */
    @Query("""
       SELECT o FROM Order o
       WHERE o.contactName IS NOT NULL
         AND (LOWER(o.contactName) LIKE LOWER(CONCAT('%', :q, '%'))
              OR o.contactPhone LIKE CONCAT('%', :q, '%'))
       ORDER BY o.id DESC
    """)
    List<Order> searchByContact(String q);

    /**
     * Розница к отправке. Неоплаченные и возвращённые заказы не показываем;
     * у АМО-заказов оплат на беке нет (paymentStatus = NONE) — они проходят фильтр.
     */
    @Query("""
       SELECT o FROM Order o
       WHERE o.isRetail = TRUE
         AND (o.tracker IS NULL OR o.tracker = '')
         AND o.paymentStatus NOT IN (ru.anyforms.model.OrderPaymentStatus.AWAITING_PAYMENT,
                                     ru.anyforms.model.OrderPaymentStatus.CANCELED,
                                     ru.anyforms.model.OrderPaymentStatus.REFUNDED)
       ORDER BY o.purchaseDate
       """)
    List<Order> findOrdersWithoutTracker();

    List<Order> findOrdersByDeliveryStatus(String deliveryStatus);

    @Query("""
       SELECT o FROM Order o
       WHERE o.tracker IS NOT NULL
         AND o.isRetail = TRUE
         AND o.tracker <> ''
         AND o.deliveryStatus IS NOT NULL
         AND o.deliveryStatus <> ''
         AND o.deliveryStatus <> :notDeliveryStatus
         AND o.deliveryStatus <> :notDeliveryStatus2
       order by o.purchaseDate
       """)
    List<Order> findOrdersFilledTrackerExceptDeliveryStatus(String notDeliveryStatus, String notDeliveryStatus2);


    Optional<Order> findFirstByTrackerOrderByIdDesc(String tracker);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE orders
            SET last_delivery_notification = :notification
            WHERE id = :id
              AND last_delivery_notification IS NULL
            """, nativeQuery = true)
    int claimFirstDeliveryNotification(@Param("id") Long id, @Param("notification") String notification);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE orders
            SET last_delivery_notification = :notification
            WHERE id = :id
              AND (last_delivery_notification IS NULL OR last_delivery_notification IN (:earlier))
            """, nativeQuery = true)
    int claimNextDeliveryNotification(@Param("id") Long id,
                                      @Param("notification") String notification,
                                      @Param("earlier") Collection<String> earlier);

    @Query("""
       SELECT o FROM Order o
       WHERE o.tracker IS NOT NULL
         AND o.tracker <> ''
         AND o.isRetail = TRUE
         AND (
              o.deliveryStatus IS NULL
              OR o.deliveryStatus = ''
              OR o.deliveryStatus = 'CREATED'
         )
       order by o.purchaseDate
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
                  AND o.tracker IS NOT NULL
                  AND o.tracker <> ''
            """)
    List<Order> getNonDeliveredOrders();

    /**
     * Оплаченные заказы витрины магазина за период — для отчёта по продажам партнёра.
     * REFUNDED сюда не попадает: деньги вернули, продажей не считаем.
     */
    @Query("""
       SELECT o FROM Order o
       WHERE o.shop.slug = :slug
         AND o.paymentStatus = ru.anyforms.model.OrderPaymentStatus.PAID
         AND o.purchaseDate >= :from
         AND o.purchaseDate < :to
       ORDER BY o.purchaseDate
       """)
    List<Order> findPaidShopOrders(String slug, LocalDateTime from, LocalDateTime to);

    /** Розничные заказы, ожидающие отправки (цех ещё не поставил трекер). */
    @Query("""
       SELECT COUNT(o) FROM Order o
       WHERE o.isRetail = TRUE
         AND (o.tracker IS NULL OR o.tracker = '')
         AND o.paymentStatus NOT IN (ru.anyforms.model.OrderPaymentStatus.AWAITING_PAYMENT,
                                     ru.anyforms.model.OrderPaymentStatus.CANCELED,
                                     ru.anyforms.model.OrderPaymentStatus.REFUNDED)
       """)
    long countRetailAwaitingShipment();

    /** Розничные заказы с трекером, ещё не доставленные. */
    @Query("""
       SELECT COUNT(o) FROM Order o
       WHERE o.isRetail = TRUE
         AND o.tracker IS NOT NULL
         AND o.tracker <> ''
         AND (o.deliveryStatus IS NULL OR o.deliveryStatus <> 'DELIVERED')
       """)
    long countRetailInDelivery();

    @Query(value = """
       SELECT EXISTS (
           SELECT 1 FROM orders o
           WHERE o.retail = TRUE
             AND o.payment_status IN ('NONE', 'PAID', 'REFUNDED')
             AND ((:email <> '' AND lower(coalesce(o.email, '')) = lower(:email))
                  OR (:phoneLast10 <> ''
                      AND right(regexp_replace(coalesce(o.contact_phone, ''), '\\D', '', 'g'), 10) = :phoneLast10)
                  OR (:deviceId <> '' AND coalesce(o.device_id, '') = :deviceId))
       )
       """, nativeQuery = true)
    boolean existsRetailOrderByCustomer(@Param("email") String email,
                                        @Param("phoneLast10") String phoneLast10,
                                        @Param("deviceId") String deviceId);
}
