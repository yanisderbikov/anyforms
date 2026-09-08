package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoSalesbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты справочника ботов: кэш на TTL, принудительное обновление, устаревший снимок
 * при недоступности amoCRM и «мягкий» {@code namesById}.
 */
class SalesbotDirectoryImplTest {

    private static final List<AmoSalesbot> BOTS = List.of(
            new AmoSalesbot(101L, "Привет", "regular"),
            new AmoSalesbot(102L, null, "regular"),
            new AmoSalesbot(103L, "Напоминание", "marketing"));

    private final AmoCrmGateway gateway = mock(AmoCrmGateway.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-07T10:00:00Z"));
    private final SalesbotDirectoryImpl directory = new SalesbotDirectoryImpl(gateway, clock);

    @Test
    void bots_cachesWithinTtl_andRefreshBypassesCache() {
        when(gateway.getSalesbots()).thenReturn(BOTS);

        assertEquals(BOTS, directory.bots(false));
        assertEquals(BOTS, directory.bots(false));
        verify(gateway, times(1)).getSalesbots();

        directory.bots(true);
        verify(gateway, times(2)).getSalesbots();
    }

    @Test
    void bots_reloadsAfterTtl() {
        when(gateway.getSalesbots()).thenReturn(BOTS);
        directory.bots(false);

        clock.advance(SalesbotDirectoryImpl.TTL.plusSeconds(1));
        directory.bots(false);

        verify(gateway, times(2)).getSalesbots();
    }

    @Test
    void bots_servesStaleSnapshot_whenAmoFailsOnRefresh() {
        when(gateway.getSalesbots()).thenReturn(BOTS).thenThrow(new RuntimeException("amo 503"));
        directory.bots(false);
        clock.advance(SalesbotDirectoryImpl.TTL.plusSeconds(1));

        assertEquals(BOTS, directory.bots(true));
    }

    @Test
    void bots_throws_whenAmoFailsAndNothingCached_butNamesStayEmpty() {
        when(gateway.getSalesbots()).thenThrow(new RuntimeException("amo 503"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> directory.bots(false));
        assertTrue(e.getMessage().contains("amo 503"));
        assertEquals(Map.of(), directory.namesById());
    }

    @Test
    void namesById_skipsBotsWithoutName() {
        when(gateway.getSalesbots()).thenReturn(BOTS);

        assertEquals(Map.of(101L, "Привет", 103L, "Напоминание"), directory.namesById());
    }

    /** Часы, которые можно двигать вперёд руками. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
