package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.service.salesbot.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты доменной логики выбора следующего бота: «первая позиция без success-записи».
 */
class NextBotResolverImplTest {

    private final BotSequenceProvider sequenceProvider = mock(BotSequenceProvider.class);
    private final BotExecutionReader executionReader = mock(BotExecutionReader.class);
    private final NextBotResolver resolver = new NextBotResolverImpl(sequenceProvider, executionReader);

    private static final List<BotStep> CHAIN = List.of(
            new BotStep(101L, 1), new BotStep(102L, 2), new BotStep(103L, 3));

    @Test
    void returnsFirstBot_whenNothingDone() {
        when(sequenceProvider.sequenceFor(OrderType.RETAIL)).thenReturn(CHAIN);
        when(executionReader.successPositions(1L, OrderType.RETAIL)).thenReturn(Set.of());

        Optional<BotStep> next = resolver.nextBot(OrderType.RETAIL, 1L);

        assertTrue(next.isPresent());
        assertEquals(101L, next.get().botId());
        assertEquals(1, next.get().position());
    }

    @Test
    void returnsThirdBot_whenFirstTwoSucceeded() {
        when(sequenceProvider.sequenceFor(OrderType.RETAIL)).thenReturn(CHAIN);
        when(executionReader.successPositions(2L, OrderType.RETAIL)).thenReturn(Set.of(1, 2));

        assertEquals(103L, resolver.nextBot(OrderType.RETAIL, 2L).orElseThrow().botId());
    }

    @Test
    void returnsEmpty_whenWholeChainSucceeded() {
        when(sequenceProvider.sequenceFor(OrderType.RETAIL)).thenReturn(CHAIN);
        when(executionReader.successPositions(3L, OrderType.RETAIL)).thenReturn(Set.of(1, 2, 3));

        assertTrue(resolver.nextBot(OrderType.RETAIL, 3L).isEmpty());
    }

    @Test
    void returnsEmpty_whenSequenceIsEmpty() {
        when(sequenceProvider.sequenceFor(OrderType.CUSTOM)).thenReturn(List.of());

        assertTrue(resolver.nextBot(OrderType.CUSTOM, 9L).isEmpty());
    }

    /**
     * Прогресс считается ТОЛЬКО по success: если позиция 3 success, а 2 ещё нет
     * (например, ранее был FAILED из-за выхода из статуса), следующей будет именно 2.
     * Тот же механизм делает только что добавленную позицию «следующей» для всех,
     * у кого предыдущие уже success.
     */
    @Test
    void picksFirstGap_notHighestDone() {
        when(sequenceProvider.sequenceFor(OrderType.RETAIL)).thenReturn(CHAIN);
        when(executionReader.successPositions(4L, OrderType.RETAIL)).thenReturn(Set.of(1, 3));

        assertEquals(102L, resolver.nextBot(OrderType.RETAIL, 4L).orElseThrow().botId());
    }
}
