package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.salesbot.BotAnalyticsDTO;
import ru.anyforms.dto.salesbot.BotAnalyticsStepDTO;
import ru.anyforms.dto.salesbot.BotAnalyticsTypeDTO;
import ru.anyforms.dto.salesbot.BotExecutionLogPageDTO;
import ru.anyforms.model.salesbot.BotExecutionLog;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.repository.BotExecutionLogRepository;
import ru.anyforms.repository.BotSequenceRepository;
import ru.anyforms.repository.BotStepStatusCount;
import ru.anyforms.service.salesbot.BotExecutionAnalyticsService;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты аналитики журнала: агрегаты по шагам, шаги текущей цепочки с нулями,
 * границы периода по Москве и постраничный журнал.
 */
class BotExecutionAnalyticsServiceImplTest {

    private final BotExecutionLogRepository logRepository = mock(BotExecutionLogRepository.class);
    private final BotSequenceRepository sequenceRepository = mock(BotSequenceRepository.class);
    private final SalesbotDirectory salesbotDirectory = mock(SalesbotDirectory.class);
    private final BotExecutionAnalyticsService service =
            new BotExecutionAnalyticsServiceImpl(logRepository, sequenceRepository, salesbotDirectory);

    @Test
    void analytics_aggregatesPerStep_addsConfiguredStepsWithZeros_andMarksSequenceMembership() {
        when(logRepository.countByStepAndStatus(any(), any())).thenReturn(List.of(
                new BotStepStatusCount(OrderType.RETAIL, 1, 101L, BotExecutionStatus.SUCCESS, 10L),
                new BotStepStatusCount(OrderType.RETAIL, 1, 101L, BotExecutionStatus.MESSAGE_SEND_FAILED, 2L),
                new BotStepStatusCount(OrderType.RETAIL, 2, 102L, BotExecutionStatus.SUCCESS, 5L),
                new BotStepStatusCount(OrderType.RETAIL, 2, 102L, BotExecutionStatus.FAILED, 3L),
                new BotStepStatusCount(OrderType.MANUAL, 0, 900L, BotExecutionStatus.SUCCESS, 4L)));
        when(logRepository.countDistinctLeads(any(), any())).thenReturn(12L);
        when(sequenceRepository.findAll()).thenReturn(List.of(
                step(OrderType.RETAIL, 1, 101L),
                step(OrderType.RETAIL, 2, 102L),
                step(OrderType.RETAIL, 3, 103L)));
        when(salesbotDirectory.namesById()).thenReturn(Map.of(101L, "Привет"));

        BotAnalyticsDTO dto = service.analytics(null, null);

        assertEquals(19, dto.totals().sent());
        assertEquals(2, dto.totals().blocked());
        assertEquals(3, dto.totals().failed());
        assertEquals(12, dto.totals().leads());
        assertEquals(List.of("RETAIL", "MANUAL"), dto.types().stream().map(BotAnalyticsTypeDTO::type).toList());

        BotAnalyticsTypeDTO retail = dto.types().get(0);
        assertEquals(15, retail.sent());
        assertEquals(2, retail.blocked());
        assertEquals(3, retail.failed());
        assertEquals(List.of(1, 2, 3), retail.steps().stream().map(BotAnalyticsStepDTO::position).toList());

        BotAnalyticsStepDTO first = retail.steps().get(0);
        assertEquals(10, first.sent());
        assertEquals(2, first.blocked());
        assertEquals(2.0 / 12, first.blockedShare(), 1e-9);
        assertTrue(first.inSequence());
        assertEquals("Привет", first.botName());
        assertNull(retail.steps().get(1).botName());

        // Третий шаг настроен, но за период по нему записей нет — отдаётся с нулями.
        BotAnalyticsStepDTO third = retail.steps().get(2);
        assertEquals(103L, third.botId());
        assertEquals(0, third.sent());
        assertNull(third.blockedShare());
        assertTrue(third.inSequence());

        // Ручной запуск: в bot_sequence его нет.
        BotAnalyticsStepDTO manual = dto.types().get(1).steps().get(0);
        assertEquals(0, manual.position());
        assertFalse(manual.inSequence());
    }

    @Test
    void analytics_convertsMoscowCalendarDaysToInstantRange() {
        when(logRepository.countByStepAndStatus(any(), any())).thenReturn(List.of());
        when(sequenceRepository.findAll()).thenReturn(List.of());

        BotAnalyticsDTO dto = service.analytics(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

        // 01.09 00:00 МСК = 31.08 21:00 UTC; конец — начало 08.09 по МСК (исключительно).
        Instant from = Instant.parse("2026-08-31T21:00:00Z");
        Instant to = Instant.parse("2026-09-07T21:00:00Z");
        verify(logRepository).countByStepAndStatus(from, to);
        verify(logRepository).countDistinctLeads(from, to);
        assertEquals(from.toString(), dto.from());
        assertEquals(to.toString(), dto.to());
        assertTrue(dto.types().isEmpty());
    }

    @Test
    void analytics_rejectsInvertedPeriod() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.analytics(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(logRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void logs_clampsPagingAndMapsEntries() {
        BotExecutionLog log = new BotExecutionLog();
        log.setId(1L);
        log.setLeadId(50399385L);
        log.setBotId(101L);
        log.setPosition(1);
        log.setType(OrderType.RETAIL);
        log.setStatus(BotExecutionStatus.MESSAGE_SEND_FAILED);
        log.setDateExecuted(Instant.parse("2026-09-01T10:00:00Z"));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(logRepository.findAll(any(Specification.class), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 200), 1));
        when(salesbotDirectory.namesById()).thenReturn(Map.of(101L, "Привет"));

        BotExecutionLogPageDTO page = service.logs(null, BotExecutionStatus.MESSAGE_SEND_FAILED, null,
                null, null, -5, 10_000);

        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(200, pageable.getValue().getPageSize());
        assertEquals(1, page.totalElements());
        assertEquals(1, page.content().size());
        assertEquals(50399385L, page.content().get(0).leadId());
        assertEquals("Привет", page.content().get(0).botName());
        assertEquals(BotExecutionStatus.MESSAGE_SEND_FAILED, page.content().get(0).status());
        assertEquals("2026-09-01T10:00:00Z", page.content().get(0).dateExecuted());
    }

    private static BotSequence step(OrderType type, int position, Long botId) {
        BotSequence s = new BotSequence();
        s.setType(type);
        s.setPosition(position);
        s.setBotId(botId);
        return s;
    }
}
