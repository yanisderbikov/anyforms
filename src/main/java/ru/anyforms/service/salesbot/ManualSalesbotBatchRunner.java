package ru.anyforms.service.salesbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.salesbot.OrderType;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualSalesbotBatchRunner {

    private final AmoCrmGateway amoCrmGateway;
    private final BotExecutionReader executionReader;
    private final BotExecutionRecorder executionRecorder;

    @Async
    public void runBatch(Long pipelineId, Long statusId, Long botId) {
        List<Long> leads = amoCrmGateway.getLeadIdsByStatus(pipelineId, statusId);
        BotStep step = new BotStep(botId, 0);
        int sent = 0;
        int skipped = 0;
        for (Long leadId : leads) {
            try {
                if (executionReader.alreadyExecuted(leadId, botId)) {
                    skipped++;
                    continue;
                }
                if (amoCrmGateway.runSalesbot(leadId, botId)) {
                    executionRecorder.recordSuccess(leadId, OrderType.MANUAL, step);
                    sent++;
                } else {
                    executionRecorder.recordFailed(leadId, OrderType.MANUAL, step);
                }
            } catch (Exception e) {
                log.error("Manual batch: failed lead {} bot {}", leadId, botId, e);
            }
        }
        log.info("Manual batch done: pipeline={} status={} bot={} -> sent={}, skipped(already)={}, total={}",
                pipelineId, statusId, botId, sent, skipped, leads.size());
    }
}
