package ru.anyforms.service.salesbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.LeadFilter;
import ru.anyforms.model.salesbot.BotRunType;

import java.time.Instant;
import java.util.List;

/**
 * Фоновая часть ручного массового запуска: выбирает лидов статуса (с отбором по тегу и/или
 * признаку «Розница», см. {@link LeadFilter}), пропускает тех, кому этот бот уже уходил, остальным запускает бота и пишет журнал
 * (тип {@link BotRunType#MANUAL}, позиция 0). Ход и итоги видны через {@link ManualRun}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualSalesbotBatchRunner {

    private final AmoCrmGateway amoCrmGateway;
    private final BotExecutionReader executionReader;
    private final BotExecutionRecorder executionRecorder;

    @Async
    public void runBatch(ManualRun run) {
        try {
            List<Long> leads = amoCrmGateway.getLeadIdsByStatus(run.getPipelineId(), run.getStatusId(), run.leadFilter());
            run.markTotal(leads.size());
            BotStep step = new BotStep(run.getBotId(), 0);
            for (Long leadId : leads) {
                try {
                    if (executionReader.alreadyExecuted(leadId, run.getBotId())) {
                        run.incSkipped();
                        continue;
                    }
                    if (amoCrmGateway.runSalesbot(leadId, run.getBotId())) {
                        executionRecorder.recordSuccess(leadId, BotRunType.MANUAL, step);
                        run.incSent();
                    } else {
                        executionRecorder.recordFailed(leadId, BotRunType.MANUAL, step);
                        run.incFailed();
                    }
                } catch (Exception e) {
                    run.incFailed();
                    log.error("Manual batch #{}: failed lead {} bot {}", run.getId(), leadId, run.getBotId(), e);
                }
            }
            run.finish(Instant.now());
            log.info("Manual batch #{} done: pipeline={} status={} bot={} filter={} -> sent={}, skipped(already)={}, failed={}, total={}",
                    run.getId(), run.getPipelineId(), run.getStatusId(), run.getBotId(), run.leadFilter(),
                    run.getSent(), run.getSkipped(), run.getFailed(), leads.size());
        } catch (Exception e) {
            run.fail(Instant.now(), e.getMessage() == null ? e.toString() : e.getMessage());
            log.error("Manual batch #{} failed: pipeline={} status={} bot={}",
                    run.getId(), run.getPipelineId(), run.getStatusId(), run.getBotId(), e);
        }
    }
}
