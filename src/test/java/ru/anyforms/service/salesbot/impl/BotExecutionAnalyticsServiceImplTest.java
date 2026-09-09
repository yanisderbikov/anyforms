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
import ru.anyforms.model.salesbot.BotGroup;
import ru.anyforms.model.salesbot.BotRunType;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.repository.BotExecutionLogRepository;
import ru.anyforms.repository.BotGroupRepository;
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
 * Тесты аналитики журнала: разделы по группам и служебным типам, шаги текущей цепочки с нулями,
 * удалённая группа, границы периода по Москве и постраничный журнал.
 */
class BotExecutionAnalyticsServiceImplTest {

    private final BotExecutionLogRepository logRepository = mock(BotExecutionLogRepository.class);
    private final BotSequenceRepository sequenceRepository = mock(BotSequenceRepository.class);
    private final BotGroupRepository groupRepository = mock(BotGroupRepository.class);
    private final SalesbotDirectory salesbotDirectory = mock(SalesbotDirectory.class);
    private final BotExecutionAnalyticsService service =
            new BotExecutionAnalyticsServiceImpl(logRepository, sequenceRepository, groupRepository, salesbotDirectory);

    @Test
    void analytics_sectionsPerGroupAndServiceType_withConfiguredStepsAndDeletedGroup() {
        when(groupRepository.findAllByOrderByIdAsc()).thenReturn(List.of(group(1L, "Розница"), group(2L, "Пустая")));
        when(logRepository.countByStepAndStatus(any(), any())).thenReturn(List.of(
                new BotStepStatusCount(BotRunType.DRIP, 1L, 1, 101L, BotExecutionStatus.SUCCESS, 10L),
                new BotStepStatusCount(BotRunType.DRIP, 1L, 1, 101L, BotExecutionStatus.MESSAGE_SEND_FAILED, 2L),
                new BotStepStatusCount(BotRunType.DRIP, 1L, 2, 102L, BotExecutionStatus.SUCCESS, 5L),
                new BotStepStatusCount(BotRunType.DRIP, 1L, 2, 102L, BotExecutionStatus.FAILED, 3L),
                new BotStepStatusCount(BotRunType.DRIP, 9L, 1, 555L, BotExecutionStatus.SUCCESS, 4L),
                new BotStepStatusCount(BotRunType.MANUAL, null, 0, 900L, BotExecutionStatus.SUCCESS, 4L)));
        when(logRepository.countDistinctLeads(any(), any())).thenReturn(12L);
        when(sequenceRepository.findAllByOrderByGroupIdAscPositionAsc()).thenReturn(List.of(
                step(1L, 1, 101L), step(1L, 2, 102L), step(1L, 3, 103L)));
        when(salesbotDirectory.namesById()).thenReturn(Map.of(101L, "Привет"));

        BotAnalyticsDTO dto = service.analytics(null, null);

        assertEquals(23, dto.totals().sent()); // 10 + 5 (группа 1) + 4 (удалённая) + 4 (ручной)
        assertEquals(2, dto.totals().blocked());
        assertEquals(3, dto.totals().failed());
        assertEquals(12, dto.totals().leads());
        // Группы по id (пустая без записей и цепочки не показывается), удалённая группа, служебные типы.
        assertEquals(List.of("group:1", "group:9", "MANUAL"),
                dto.sections().stream().map(BotAnalyticsTypeDTO::key).toList());

        BotAnalyticsTypeDTO retail = dto.sections().get(0);
        assertEquals("Розница", retail.label());
        assertEquals(1L, retail.groupId());
        assertEquals(15, retail.sent());
        assertEquals(List.of(1, 2, 3), retail.steps().stream().map(BotAnalyticsStepDTO::position).toList());
        BotAnalyticsStepDTO first = retail.steps().get(0);
        assertEquals(2.0 / 12, first.blockedShare(), 1e-9);
        assertEquals("Привет", first.botName());
        assertTrue(first.inSequence());
        BotAnalyticsStepDTO third = retail.steps().get(2);
        assertEquals(0, third.sent());
        assertNull(third.blockedShare());
        assertTrue(third.inSequence());

        BotAnalyticsTypeDTO deleted = dto.sections().get(1);
        assertTrue(deleted.groupDeleted());
        assertEquals("Удалённая группа #9", deleted.label());
        assertFalse(deleted.steps().get(0).inSequence());

        BotAnalyticsTypeDTO manual = dto.sections().get(2);
        assertEquals(BotRunType.MANUAL, manual.type());
        assertNull(manual.groupId());
        assertEquals("Ручной запуск", manual.label());
        assertEquals(0, manual.steps().get(0).position());
    }

    @Test
    void analytics_convertsMoscowCalendarDaysToInstantRange() {
        when(logRepository.countByStepAndStatus(any(), any())).thenReturn(List.of());
        when(sequenceRepository.findAllByOrderByGroupIdAscPositionAsc()).thenReturn(List.of());
        when(groupRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        BotAnalyticsDTO dto = service.analytics(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

        Instant from = Instant.parse("2026-08-31T21:00:00Z");
        Instant to = Instant.parse("2026-09-07T21:00:00Z");
        verify(logRepository).countByStepAndStatus(from, to);
        verify(logRepository).countDistinctLeads(from, to);
        assertEquals(from.toString(), dto.from());
        assertEquals(to.toString(), dto.to());
        assertTrue(dto.sections().isEmpty());
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
    void logs_clampsPagingAndMapsEntries_withGroupName() {
        BotExecutionLog log = new BotExecutionLog();
        log.setId(1L);
        log.setLeadId(50399385L);
        log.setBotId(101L);
        log.setPosition(1);
        log.setType(BotRunType.DRIP);
        log.setGroupId(1L);
        log.setStatus(BotExecutionStatus.MESSAGE_SEND_FAILED);
        log.setDateExecuted(Instant.parse("2026-09-01T10:00:00Z"));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(logRepository.findAll(any(Specification.class), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 200), 1));
        when(salesbotDirectory.namesById()).thenReturn(Map.of(101L, "Привет"));
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "Розница")));

        BotExecutionLogPageDTO page = service.logs(null, 1L, BotExecutionStatus.MESSAGE_SEND_FAILED, null,
                null, null, -5, 10_000);

        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(200, pageable.getValue().getPageSize());
        assertEquals(1, page.totalElements());
        assertEquals("Привет", page.content().get(0).botName());
        assertEquals("Розница", page.content().get(0).groupName());
        assertEquals("2026-09-01T10:00:00Z", page.content().get(0).dateExecuted());
    }

    private static BotGroup group(Long id, String name) {
        BotGroup g = new BotGroup();
        g.setId(id);
        g.setName(name);
        return g;
    }

    private static BotSequence step(Long groupId, int position, Long botId) {
        BotSequence s = new BotSequence();
        s.setGroupId(groupId);
        s.setPosition(position);
        s.setBotId(botId);
        return s;
    }
}
