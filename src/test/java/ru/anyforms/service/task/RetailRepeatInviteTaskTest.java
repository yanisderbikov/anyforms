package ru.anyforms.service.task;

import org.junit.jupiter.api.Test;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.LeadFilter;
import ru.anyforms.model.salesbot.BotRunType;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotExecutionRecorder;
import ru.anyforms.service.salesbot.BotStep;
import ru.anyforms.service.salesbot.SalesbotTrigger;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Повторные продажи: отсечка по closed_at, запуск бота без смены статуса, дедуп по журналу. */
class RetailRepeatInviteTaskTest {

    private final AmoCrmGateway gateway = mock(AmoCrmGateway.class);
    private final SalesbotTrigger trigger = mock(SalesbotTrigger.class);
    private final BotExecutionReader reader = mock(BotExecutionReader.class);
    private final BotExecutionRecorder recorder = mock(BotExecutionRecorder.class);
    private final RetailRepeatInviteTask task =
            new RetailRepeatInviteTask(gateway, trigger, reader, recorder, 10557858L, 17579L, 5);

    @Test
    void runsBotForRealizedLeadsClosedFiveDaysAgo_skipsAlreadySent_doesNotChangeStatus() {
        Instant now = Instant.parse("2026-09-08T07:53:00Z");
        long fiveDaysAgo = now.getEpochSecond() - 5 * 24 * 3600;
        when(gateway.getLeadIdsByStatus(10557858L, 142L, LeadFilter.closedBefore(fiveDaysAgo)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(reader.alreadyExecuted(2L, 17579L)).thenReturn(true);
        when(trigger.run(1L, 17579L)).thenReturn(true);
        when(trigger.run(3L, 17579L)).thenReturn(false);

        task.runOnce(now);

        verify(trigger).run(1L, 17579L);
        verify(trigger, never()).run(eq(2L), any());
        verify(recorder).recordSuccess(1L, BotRunType.RETAIL_TO_REPEAT, new BotStep(17579L, 1));
        verify(recorder).recordFailed(3L, BotRunType.RETAIL_TO_REPEAT, new BotStep(17579L, 1));
        verify(gateway, never()).updateLeadStatus(any(List.class), any(), any());
        verify(gateway, never()).updateLeadStatus(any(Long.class), any(Long.class), any(Long.class));
    }
}
