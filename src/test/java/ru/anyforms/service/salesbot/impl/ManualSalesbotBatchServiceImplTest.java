package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.RunSalesbotBatchRequestDTO;
import ru.anyforms.dto.salesbot.ManualRunDTO;
import ru.anyforms.dto.salesbot.ManualRunPreviewDTO;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.salesbot.ManualRunStatus;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.ManualRun;
import ru.anyforms.service.salesbot.ManualSalesbotBatchRunner;
import ru.anyforms.service.salesbot.PipelineDirectory;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты ручного запуска: превью считает «уже получали», старт регистрирует прогон и запускает
 * фон, второй запуск параллельно не допускается.
 */
class ManualSalesbotBatchServiceImplTest {

    private final AmoCrmGateway gateway = mock(AmoCrmGateway.class);
    private final BotExecutionReader reader = mock(BotExecutionReader.class);
    private final InMemoryManualRunRegistry registry = new InMemoryManualRunRegistry();
    private final ManualSalesbotBatchRunner runner = mock(ManualSalesbotBatchRunner.class);
    private final SalesbotDirectory salesbotDirectory = mock(SalesbotDirectory.class);
    private final PipelineDirectory pipelineDirectory = mock(PipelineDirectory.class);
    private final ManualSalesbotBatchServiceImpl service = new ManualSalesbotBatchServiceImpl(
            gateway, reader, registry, runner, salesbotDirectory, pipelineDirectory);

    @Test
    void preview_countsLeadsAndAlreadySent_withNames() {
        when(gateway.getLeadIdsByStatusAndTag(10L, 20L, "лошадка")).thenReturn(List.of(1L, 2L, 3L));
        when(reader.alreadyExecuted(2L, 500L)).thenReturn(true);
        when(salesbotDirectory.namesById()).thenReturn(Map.of(500L, "Скидка"));
        when(pipelineDirectory.pipelineNames()).thenReturn(Map.of(10L, "Розница"));
        when(pipelineDirectory.statusNames()).thenReturn(Map.of(20L, "Новый заказ"));

        ManualRunPreviewDTO preview = service.preview(10L, 20L, 500L, "  лошадка ");

        assertEquals(3, preview.total());
        assertEquals(1, preview.alreadySent());
        assertEquals(2, preview.toSend());
        assertEquals("Скидка", preview.botName());
        assertEquals("Розница", preview.pipelineName());
        assertEquals("Новый заказ", preview.statusName());
    }

    @Test
    void preview_withoutTag_usesPlainStatusQuery_andWrapsAmoFailure() {
        when(gateway.getLeadIdsByStatus(10L, 20L)).thenThrow(new RuntimeException("amo 503"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.preview(10L, 20L, 500L, ""));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
        verify(gateway, never()).getLeadIdsByStatusAndTag(any(), any(), any());
    }

    @Test
    void start_registersRun_startsBackgroundJob_andRejectsSecondWhileRunning() {
        ManualRunDTO started = service.start(request(10L, 20L, 500L, " тег "), "yan");

        assertEquals(ManualRunStatus.RUNNING, started.status());
        assertEquals("тег", started.tagName());
        assertEquals("yan", started.startedBy());
        assertEquals(-1, started.total());
        verify(runner).runBatch(any(ManualRun.class));
        assertEquals(1, service.recent().size());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.start(request(10L, 20L, 501L, null), "yan"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(runner, times(1)).runBatch(any(ManualRun.class));

        // Завершили первый — можно стартовать следующий.
        registry.recent().get(0).finish(Instant.now());
        service.start(request(10L, 20L, 501L, null), "yan");
        verify(runner, times(2)).runBatch(any(ManualRun.class));
        assertEquals(2, service.recent().size());
        assertEquals(501L, service.recent().get(0).botId()); // новые сверху
    }

    private static RunSalesbotBatchRequestDTO request(Long pipelineId, Long statusId, Long botId, String tag) {
        RunSalesbotBatchRequestDTO r = new RunSalesbotBatchRequestDTO();
        r.setPipelineId(pipelineId);
        r.setStatusId(statusId);
        r.setBotId(botId);
        r.setTagName(tag);
        return r;
    }
}
