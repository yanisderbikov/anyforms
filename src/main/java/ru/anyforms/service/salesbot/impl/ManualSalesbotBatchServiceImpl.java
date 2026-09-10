package ru.anyforms.service.salesbot.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.RunSalesbotBatchRequestDTO;
import ru.anyforms.dto.salesbot.ManualRunDTO;
import ru.anyforms.dto.salesbot.ManualRunPreviewDTO;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.LeadFilter;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.ManualRun;
import ru.anyforms.service.salesbot.ManualRunRegistry;
import ru.anyforms.service.salesbot.ManualSalesbotBatchRunner;
import ru.anyforms.service.salesbot.ManualSalesbotBatchService;
import ru.anyforms.service.salesbot.PipelineDirectory;
import ru.anyforms.service.salesbot.SalesbotDirectory;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class ManualSalesbotBatchServiceImpl implements ManualSalesbotBatchService {

    private final AmoCrmGateway amoCrmGateway;
    private final BotExecutionReader executionReader;
    private final ManualRunRegistry registry;
    private final ManualSalesbotBatchRunner runner;
    private final SalesbotDirectory salesbotDirectory;
    private final PipelineDirectory pipelineDirectory;

    @Override
    public ManualRunPreviewDTO preview(Long pipelineId, Long statusId, Long botId, String tagName, Boolean retail) {
        List<Long> leads;
        try {
            leads = amoCrmGateway.getLeadIdsByStatus(pipelineId, statusId, LeadFilter.forManualRun(tagName, retail));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не удалось получить лидов из amoCRM: " + e.getMessage());
        }
        // Без бота считаем только лидов в статусе: «уже получали» знать неоткуда.
        int alreadySent = 0;
        if (botId != null) {
            for (Long leadId : leads) {
                if (executionReader.alreadyExecuted(leadId, botId)) {
                    alreadySent++;
                }
            }
        }
        Names names = names();
        return new ManualRunPreviewDTO(leads.size(), alreadySent, leads.size() - alreadySent,
                names.pipelines.get(pipelineId), names.statuses.get(statusId), botId == null ? null : names.bots.get(botId));
    }

    @Override
    public ManualRunDTO start(RunSalesbotBatchRequestDTO request, String startedBy) {
        registry.running().ifPresent(active -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Уже идёт ручной запуск #" + active.getId() + " — дождитесь его завершения.");
        });
        ManualRun run = registry.create(request.getPipelineId(), request.getStatusId(), request.getBotId(),
                normalizeTag(request.getTagName()), request.getRetail(), startedBy);
        runner.runBatch(run);
        return toDto(run, names());
    }

    @Override
    public List<ManualRunDTO> recent() {
        Names names = names();
        return registry.recent().stream().map(run -> toDto(run, names)).toList();
    }

    private static String normalizeTag(String tagName) {
        return tagName == null || tagName.isBlank() ? null : tagName.trim();
    }

    private Names names() {
        return new Names(salesbotDirectory.namesById(), pipelineDirectory.pipelineNames(), pipelineDirectory.statusNames());
    }

    private static ManualRunDTO toDto(ManualRun run, Names names) {
        return new ManualRunDTO(
                run.getId(),
                run.getStatus(),
                run.getStartedAt().toString(),
                run.getFinishedAt() != null ? run.getFinishedAt().toString() : null,
                run.getPipelineId(),
                names.pipelines.get(run.getPipelineId()),
                run.getStatusId(),
                names.statuses.get(run.getStatusId()),
                run.getBotId(),
                names.bots.get(run.getBotId()),
                run.getTagName(),
                run.getRetail(),
                run.getTotal(),
                run.getSent(),
                run.getSkipped(),
                run.getFailed(),
                run.getError(),
                run.getStartedBy());
    }

    private record Names(Map<Long, String> bots, Map<Long, String> pipelines, Map<Long, String> statuses) {
    }
}
