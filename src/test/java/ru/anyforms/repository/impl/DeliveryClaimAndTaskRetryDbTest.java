package ru.anyforms.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import ru.anyforms.model.DeliveryNotification;
import ru.anyforms.model.Order;
import ru.anyforms.model.OrderPaymentStatus;
import ru.anyforms.model.OrderSource;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.OrderRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@EnabledIfSystemProperty(named = "deliveryDb", matches = "true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PgAdvisoryTransactionLock.class)
@TestPropertySource(properties = {
        "spring.datasource.url=${delivery.db.url:jdbc:postgresql://localhost:5474/anyforms_test}",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
})
class DeliveryClaimAndTaskRetryDbTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TaskRepo taskRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PgAdvisoryTransactionLock transactionLock;

    @PersistenceContext
    private EntityManager entityManager;

    private Order order() {
        Order order = new Order();
        order.setSource(OrderSource.MARKETPLACE);
        order.setRetail(true);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setPublicId("CL" + System.nanoTime() % 10000);
        order.setEmail("buyer@b.ru");
        return orderRepository.saveAndFlush(order);
    }

    private String markerOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT last_delivery_notification FROM orders WHERE id = ?", String.class, id);
    }

    @Test
    void notificationStagesAreClaimedOnceAndOnlyForward() {
        Long id = order().getId();

        assertEquals(1, orderRepository.claimFirstDeliveryNotification(id, DeliveryNotification.SHIPPED.name()));
        assertEquals(0, orderRepository.claimFirstDeliveryNotification(id, DeliveryNotification.SHIPPED.name()));
        assertEquals(0, orderRepository.claimNextDeliveryNotification(id, DeliveryNotification.SHIPPED.name(),
                List.of("NONE")));
        assertEquals(1, orderRepository.claimNextDeliveryNotification(id, DeliveryNotification.ARRIVED_AT_PVZ.name(),
                DeliveryNotification.ARRIVED_AT_PVZ.earlierNames()));
        assertEquals(0, orderRepository.claimNextDeliveryNotification(id, DeliveryNotification.ARRIVED_AT_PVZ.name(),
                DeliveryNotification.ARRIVED_AT_PVZ.earlierNames()));
        assertEquals(0, orderRepository.claimFirstDeliveryNotification(id, DeliveryNotification.SHIPPED.name()));
        assertEquals("ARRIVED_AT_PVZ", markerOf(id));
    }

    @Test
    void entityMergeNeverOverwritesTheClaimedMarker() {
        Order detached = order();
        Long id = detached.getId();
        entityManager.clear();
        assertEquals(1, orderRepository.claimFirstDeliveryNotification(id, DeliveryNotification.SHIPPED.name()));

        detached.setLastDeliveryNotification(null);
        detached.setContactName("Иван");
        orderRepository.saveAndFlush(detached);
        entityManager.clear();

        assertEquals("SHIPPED", markerOf(id));
        assertEquals("Иван", orderRepository.findById(id).orElseThrow().getContactName());
    }

    @Test
    void retriedTasksBecomeDueOnlyAfterNextAttemptAt() {
        Instant now = Instant.now();
        Task fresh = taskRepo.saveAndFlush(Task.builder().type(TaskType.DELIVERY_STATUS_EMAIL).status(TaskStatus.NEW).payload("{}").build());
        Task later = taskRepo.saveAndFlush(Task.builder().type(TaskType.DELIVERY_STATUS_EMAIL).status(TaskStatus.NEW).payload("{}")
                .attempts(1).nextAttemptAt(now.plus(Duration.ofMinutes(5))).build());
        Task due = taskRepo.saveAndFlush(Task.builder().type(TaskType.DELIVERY_STATUS_EMAIL).status(TaskStatus.NEW).payload("{}")
                .attempts(2).nextAttemptAt(now.minus(Duration.ofSeconds(1))).build());

        List<Task> batch = taskRepo.findDue(TaskType.DELIVERY_STATUS_EMAIL, TaskStatus.NEW, now, PageRequest.of(0, 10));

        assertEquals(List.of(fresh.getId(), due.getId()), batch.stream().map(Task::getId).toList());
        assertNull(fresh.getNextAttemptAt());
        assertEquals(1, later.getAttempts());
    }

    @Test
    void advisoryLockRunsInsideTheTestTransaction() {
        transactionLock.lockAll(List.of("promo-popup:test:device:b", "promo-popup:test:device:a"));
        transactionLock.lock("promo-popup:test:device:a");

        Long held = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()", Long.class);
        assertEquals(2L, held);
    }
}
