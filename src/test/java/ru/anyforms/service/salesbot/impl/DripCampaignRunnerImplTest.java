package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.service.salesbot.*;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты оркестрации одного прогона: ветки success / выход из статуса / ошибка запуска /
 * завершённая цепочка.
 */
class DripCampaignRunnerImplTest {

    private final OrderTypeFunnelDirectory funnelDirectory = mock(OrderTypeFunnelDirectory.class);
    private final LeadProvider leadProvider = mock(LeadProvider.class);
    private final NextBotResolver nextBotResolver = mock(NextBotResolver.class);
    private final LeadStatusVerifier statusVerifier = mock(LeadStatusVerifier.class);
    private final SalesbotTrigger trigger = mock(SalesbotTrigger.class);
    private final BotExecutionRecorder recorder = mock(BotExecutionRecorder.class);
    private final BotExecutionReader reader = mock(BotExecutionReader.class);

    private final DripCampaignRunner runner = new DripCampaignRunnerImpl(
            funnelDirectory, leadProvider, nextBotResolver, statusVerifier, trigger, recorder, reader);

    private static final FunnelTarget TARGET = new FunnelTarget(900L, 142L);
    private static final BotStep STEP = new BotStep(101L, 1);

    @Test
    void runsBot_recordsSuccess_verifiesStatus_skipsFinishedChains() {
        when(funnelDirectory.configuredTypes()).thenReturn(List.of(OrderType.RETAIL));
        when(funnelDirectory.targetFor(OrderType.RETAIL)).thenReturn(Optional.of(TARGET));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L, 2L, 3L, 4L));

        // lead 1: следующий есть, в статусе, запуск успешен -> SUCCESS
        when(nextBotResolver.nextBot(OrderType.RETAIL, 1L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(1L, TARGET)).thenReturn(true);
        when(trigger.run(1L, 101L)).thenReturn(true);

        // lead 2: вышел из статуса между запросом №1 и запуском -> FAILED, бот не запускаем
        when(nextBotResolver.nextBot(OrderType.RETAIL, 2L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(2L, TARGET)).thenReturn(false);

        // lead 3: в статусе, но запрос на запуск упал -> FAILED
        when(nextBotResolver.nextBot(OrderType.RETAIL, 3L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(3L, TARGET)).thenReturn(true);
        when(trigger.run(3L, 101L)).thenReturn(false);

        // lead 4: цепочка завершена -> ничего не делаем
        when(nextBotResolver.nextBot(OrderType.RETAIL, 4L)).thenReturn(Optional.empty());

        runner.runOnce();

        verify(trigger).run(1L, 101L);
        verify(recorder).recordSuccess(1L, OrderType.RETAIL, STEP);

        verify(trigger, never()).run(eq(2L), anyLong());
        verify(recorder).recordFailed(2L, OrderType.RETAIL, STEP);

        verify(trigger).run(3L, 101L); // бот для lead 3 запускался, но запрос упал
        verify(recorder).recordFailed(3L, OrderType.RETAIL, STEP);

        // lead 4: статус даже не перечитываем, ничего не пишем
        verify(statusVerifier, never()).isInTargetStatus(eq(4L), any());
        verifyNoMoreInteractions(trigger);
    }

    @Test
    void skipsType_whenNoFunnelConfigured() {
        when(funnelDirectory.configuredTypes()).thenReturn(List.of(OrderType.CUSTOM));
        when(funnelDirectory.targetFor(OrderType.CUSTOM)).thenReturn(Optional.empty());

        runner.runOnce();

        verifyNoInteractions(leadProvider, nextBotResolver, statusVerifier, trigger, recorder, reader);
    }

    @Test
    void skipsLead_whenAlreadySentToday() {
        when(funnelDirectory.configuredTypes()).thenReturn(List.of(OrderType.RETAIL));
        when(funnelDirectory.targetFor(OrderType.RETAIL)).thenReturn(Optional.of(TARGET));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L));
        when(reader.alreadySentToday(eq(1L), any())).thenReturn(true); // лиду уже слали сегодня

        runner.runOnce();

        // ничего больше не делаем: ни выбора бота, ни проверки статуса, ни отправки/записи
        verify(nextBotResolver, never()).nextBot(any(), anyLong());
        verifyNoInteractions(statusVerifier, trigger, recorder);
    }

    @Test
    void oneFailingLeadDoesNotAbortTheRest() {
        when(funnelDirectory.configuredTypes()).thenReturn(List.of(OrderType.RETAIL));
        when(funnelDirectory.targetFor(OrderType.RETAIL)).thenReturn(Optional.of(TARGET));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L, 2L));

        // lead 1 кидает исключение, lead 2 должен всё равно обработаться
        when(nextBotResolver.nextBot(OrderType.RETAIL, 1L)).thenThrow(new RuntimeException("boom"));
        when(nextBotResolver.nextBot(OrderType.RETAIL, 2L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(2L, TARGET)).thenReturn(true);
        when(trigger.run(2L, 101L)).thenReturn(true);

        runner.runOnce();

        verify(recorder).recordSuccess(2L, OrderType.RETAIL, STEP);
    }
}
