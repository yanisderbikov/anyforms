package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.service.salesbot.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты оркестрации одного прогона по группам: ветки success / выход из статуса / ошибка
 * запуска / завершённая цепочка / уже отправленный бот.
 */
class DripCampaignRunnerImplTest {

    private final BotGroupDirectory groupDirectory = mock(BotGroupDirectory.class);
    private final LeadProvider leadProvider = mock(LeadProvider.class);
    private final NextBotResolver nextBotResolver = mock(NextBotResolver.class);
    private final LeadStatusVerifier statusVerifier = mock(LeadStatusVerifier.class);
    private final SalesbotTrigger trigger = mock(SalesbotTrigger.class);
    private final BotExecutionRecorder recorder = mock(BotExecutionRecorder.class);
    private final BotExecutionReader reader = mock(BotExecutionReader.class);
    private final LeadSeenStore seenStore = mock(LeadSeenStore.class);

    private final DripCampaignRunner runner = new DripCampaignRunnerImpl(
            groupDirectory, leadProvider, nextBotResolver, statusVerifier, trigger, recorder, reader, seenStore);

    private static final FunnelTarget TARGET = new FunnelTarget(900L, 142L);
    /** Окно на все сутки — время в тестах ветвлений не мешает. */
    private static final TimeWindow ALL_DAY = new TimeWindow(LocalTime.MIDNIGHT, LocalTime.of(23, 59));
    private static final ActiveGroup GROUP = new ActiveGroup(7L, "Розница", TARGET, ALL_DAY);
    /** Шаг без задержки — в тестах ветвлений время не мешает. */
    private static final BotStep STEP = new BotStep(101L, 1, 0);

    {
        // Якорь: по умолчанию «увидели давно», чтобы задержки не блокировали ветки ниже.
        when(seenStore.firstSeenOrRecord(anyLong(), anyLong(), any()))
                .thenReturn(Instant.now().minus(Duration.ofDays(30)));
        when(reader.lastSuccessAt(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void runsBot_recordsSuccess_verifiesStatus_skipsFinishedChains() {
        when(groupDirectory.activeGroups()).thenReturn(List.of(GROUP));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L, 2L, 3L, 4L));

        // lead 1: следующий есть, в статусе, запуск успешен -> SUCCESS
        when(nextBotResolver.nextBot(7L, 1L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(1L, TARGET)).thenReturn(true);
        when(trigger.run(1L, 101L)).thenReturn(true);

        // lead 2: вышел из статуса между запросом №1 и запуском -> FAILED, бот не запускаем
        when(nextBotResolver.nextBot(7L, 2L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(2L, TARGET)).thenReturn(false);

        // lead 3: в статусе, но запрос на запуск упал -> FAILED
        when(nextBotResolver.nextBot(7L, 3L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(3L, TARGET)).thenReturn(true);
        when(trigger.run(3L, 101L)).thenReturn(false);

        // lead 4: цепочка завершена -> ничего не делаем
        when(nextBotResolver.nextBot(7L, 4L)).thenReturn(Optional.empty());

        runner.runOnce();

        verify(trigger).run(1L, 101L);
        verify(recorder).recordGroupSuccess(1L, 7L, STEP);

        verify(trigger, never()).run(eq(2L), anyLong());
        verify(recorder).recordGroupFailed(2L, 7L, STEP);

        verify(trigger).run(3L, 101L);
        verify(recorder).recordGroupFailed(3L, 7L, STEP);

        verify(statusVerifier, never()).isInTargetStatus(eq(4L), any());
        verifyNoMoreInteractions(trigger);
    }

    @Test
    void doesNothing_whenNoActiveGroups() {
        when(groupDirectory.activeGroups()).thenReturn(List.of());

        runner.runOnce();

        verifyNoInteractions(leadProvider, nextBotResolver, statusVerifier, trigger, recorder, reader, seenStore);
    }

    /** Шаг 1 с задержкой: пока не прошло N минут с первого появления в статусе — ждём. */
    @Test
    void waitsForFirstStepDelay_fromFirstSeen() {
        BotStep delayed = new BotStep(101L, 1, 24 * 60);
        when(groupDirectory.activeGroups()).thenReturn(List.of(GROUP));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L));
        when(nextBotResolver.nextBot(7L, 1L)).thenReturn(Optional.of(delayed));
        when(seenStore.firstSeenOrRecord(eq(7L), eq(1L), any())).thenReturn(Instant.now().minus(Duration.ofHours(2)));

        runner.runOnce();

        verifyNoInteractions(statusVerifier, trigger, recorder);
    }

    /** Шаг 2 с задержкой считается от успешной отправки предыдущего шага, а не от первого появления. */
    @Test
    void secondStepDelay_countsFromPreviousSuccess() {
        BotStep second = new BotStep(102L, 2, 3 * 24 * 60);
        when(groupDirectory.activeGroups()).thenReturn(List.of(GROUP));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L, 2L));
        when(nextBotResolver.nextBot(eq(7L), anyLong())).thenReturn(Optional.of(second));
        when(statusVerifier.isInTargetStatus(anyLong(), eq(TARGET))).thenReturn(true);
        when(trigger.run(anyLong(), eq(102L))).thenReturn(true);
        // lead 1: предыдущий шаг был 4 дня назад -> пора; lead 2: вчера -> ждём
        when(reader.lastSuccessAt(1L, 7L)).thenReturn(Optional.of(Instant.now().minus(Duration.ofDays(4))));
        when(reader.lastSuccessAt(2L, 7L)).thenReturn(Optional.of(Instant.now().minus(Duration.ofDays(1))));

        runner.runOnce();

        verify(trigger).run(1L, 102L);
        verify(recorder).recordGroupSuccess(1L, 7L, second);
        verify(trigger, never()).run(eq(2L), anyLong());
    }

    /** Задержка истекла, но сейчас вне окна отправки группы — ждём следующего открытия окна. */
    @Test
    void waitsForGroupSendWindow_whenDueOutsideIt() {
        LocalTime nowMsk = Instant.now().atZone(ZoneId.of("Europe/Moscow")).toLocalTime();
        // Окно из одной минуты, заведомо не содержащей «сейчас».
        LocalTime from = nowMsk.getHour() >= 12 ? LocalTime.of(1, 0) : LocalTime.of(22, 0);
        ActiveGroup nightGroup = new ActiveGroup(7L, "Розница", TARGET, new TimeWindow(from, from.plusMinutes(1)));
        when(groupDirectory.activeGroups()).thenReturn(List.of(nightGroup));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L));
        when(nextBotResolver.nextBot(7L, 1L)).thenReturn(Optional.of(STEP));

        runner.runOnce();

        verifyNoInteractions(statusVerifier, trigger, recorder);
    }

    /** Бот лиду уже уходил (цепочку переставили): повторно не шлём, позицию засчитываем. */
    @Test
    void doesNotResendBotAlreadyExecutedForLead_butAdvancesProgress() {
        when(groupDirectory.activeGroups()).thenReturn(List.of(GROUP));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L));
        when(nextBotResolver.nextBot(7L, 1L)).thenReturn(Optional.of(STEP));
        when(reader.alreadyExecuted(1L, 101L)).thenReturn(true);

        runner.runOnce();

        verify(recorder).recordGroupSuccess(1L, 7L, STEP);
        verifyNoInteractions(statusVerifier, trigger);
    }

    @Test
    void oneFailingLeadDoesNotAbortTheRest() {
        when(groupDirectory.activeGroups()).thenReturn(List.of(GROUP));
        when(leadProvider.leadsInStatus(TARGET)).thenReturn(List.of(1L, 2L));
        when(nextBotResolver.nextBot(7L, 1L)).thenThrow(new RuntimeException("boom"));
        when(nextBotResolver.nextBot(7L, 2L)).thenReturn(Optional.of(STEP));
        when(statusVerifier.isInTargetStatus(2L, TARGET)).thenReturn(true);
        when(trigger.run(2L, 101L)).thenReturn(true);

        runner.runOnce();

        verify(recorder).recordGroupSuccess(2L, 7L, STEP);
    }
}
