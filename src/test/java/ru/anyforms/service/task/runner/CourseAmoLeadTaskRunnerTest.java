package ru.anyforms.service.task.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.payment.CourseAmoLeadService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseAmoLeadTaskRunnerTest {

    private final GetterTaskByStatus getterTaskByStatus = mock(GetterTaskByStatus.class);
    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final CourseAmoLeadService courseAmoLeadService = mock(CourseAmoLeadService.class);
    private final SaverTask saverTask = mock(SaverTask.class);
    private final CourseAmoLeadTaskRunner runner =
            new CourseAmoLeadTaskRunner(getterTaskByStatus, getterTransaction, courseAmoLeadService, saverTask);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(runner, "batchSize", 10);
    }

    private static Task task(UUID transactionId) {
        return Task.builder()
                .id(UUID.randomUUID())
                .type(TaskType.AMO_COURSE_BOUGHT)
                .status(TaskStatus.NEW)
                .payload("{\"transactionId\":\"" + transactionId + "\"}")
                .build();
    }

    @Test
    void loadsTransactionByIdFromPayloadAndPushesToAmo() {
        UUID transactionId = UUID.randomUUID();
        Task task = task(transactionId);
        PaymentTransaction transaction = PaymentTransaction.builder().id(transactionId).build();

        when(getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_COURSE_BOUGHT, TaskStatus.NEW, 10))
                .thenReturn(List.of(task));
        when(getterTransaction.getById(transactionId)).thenReturn(Optional.of(transaction));

        runner.runBatch();

        verify(courseAmoLeadService).pushCoursePurchase(transaction);
        assertEquals(TaskStatus.DONE, task.getStatus());
    }

    @Test
    void marksTaskFailedWhenTransactionNotFound() {
        UUID transactionId = UUID.randomUUID();
        Task task = task(transactionId);

        when(getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_COURSE_BOUGHT, TaskStatus.NEW, 10))
                .thenReturn(List.of(task));
        when(getterTransaction.getById(transactionId)).thenReturn(Optional.empty());

        runner.runBatch();

        verify(courseAmoLeadService, never()).pushCoursePurchase(any());
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertTrue(task.getComment().contains(transactionId.toString()));
    }

    @Test
    void marksTaskFailedWhenAmoPushThrows() {
        UUID transactionId = UUID.randomUUID();
        Task task = task(transactionId);
        PaymentTransaction transaction = PaymentTransaction.builder().id(transactionId).build();

        when(getterTaskByStatus.getByTaskTypeAndStatus(TaskType.AMO_COURSE_BOUGHT, TaskStatus.NEW, 10))
                .thenReturn(List.of(task));
        when(getterTransaction.getById(transactionId)).thenReturn(Optional.of(transaction));
        doThrow(new IllegalStateException("нет активной сделки")).when(courseAmoLeadService)
                .pushCoursePurchase(transaction);

        runner.runBatch();

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("нет активной сделки", task.getComment());
    }
}
