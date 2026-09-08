package ru.anyforms.service.salesbot;

import org.junit.jupiter.api.Test;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.salesbot.ManualRunStatus;
import ru.anyforms.model.salesbot.OrderType;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Тесты фоновой части ручного запуска: счётчики, пропуск уже получавших, ошибка выборки. */
class ManualSalesbotBatchRunnerTest {

    private final AmoCrmGateway gateway = mock(AmoCrmGateway.class);
    private final BotExecutionReader reader = mock(BotExecutionReader.class);
    private final BotExecutionRecorder recorder = mock(BotExecutionRecorder.class);
    private final ManualSalesbotBatchRunner runner = new ManualSalesbotBatchRunner(gateway, reader, recorder);

    @Test
    void countsSentSkippedFailed_andFinishes() {
        ManualRun run = new ManualRun(1L, Instant.now(), 10L, 20L, 500L, null, "yan");
        when(gateway.getLeadIdsByStatus(10L, 20L)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(reader.alreadyExecuted(2L, 500L)).thenReturn(true);
        when(gateway.runSalesbot(1L, 500L)).thenReturn(true);
        when(gateway.runSalesbot(3L, 500L)).thenReturn(false);
        when(gateway.runSalesbot(4L, 500L)).thenThrow(new RuntimeException("boom"));

        runner.runBatch(run);

        assertEquals(ManualRunStatus.DONE, run.getStatus());
        assertEquals(4, run.getTotal());
        assertEquals(1, run.getSent());
        assertEquals(1, run.getSkipped());
        assertEquals(2, run.getFailed());
        assertNotNull(run.getFinishedAt());
        verify(recorder).recordSuccess(eq(1L), eq(OrderType.MANUAL), any(BotStep.class));
        verify(recorder).recordFailed(eq(3L), eq(OrderType.MANUAL), any(BotStep.class));
        verify(gateway, never()).runSalesbot(2L, 500L);
    }

    @Test
    void usesTagQuery_whenTagGiven() {
        ManualRun run = new ManualRun(2L, Instant.now(), 10L, 20L, 500L, "лошадка", "yan");
        when(gateway.getLeadIdsByStatusAndTag(10L, 20L, "лошадка")).thenReturn(List.of());

        runner.runBatch(run);

        assertEquals(ManualRunStatus.DONE, run.getStatus());
        assertEquals(0, run.getTotal());
        verify(gateway, never()).getLeadIdsByStatus(any(), any());
    }

    @Test
    void marksFailed_whenLeadListingFails() {
        ManualRun run = new ManualRun(3L, Instant.now(), 10L, 20L, 500L, null, "yan");
        when(gateway.getLeadIdsByStatus(10L, 20L)).thenThrow(new RuntimeException("amo 503"));

        runner.runBatch(run);

        assertEquals(ManualRunStatus.FAILED, run.getStatus());
        assertEquals("amo 503", run.getError());
        assertEquals(-1, run.getTotal());
        verifyNoInteractions(recorder);
    }
}
