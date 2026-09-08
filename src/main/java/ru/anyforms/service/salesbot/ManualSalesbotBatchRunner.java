package ru.anyforms.service.salesbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.salesbot.OrderType;

import java.time.Instant;
import java.util.List;

/**
 * Фоновая часть ручного массового запуска: выбирает лидов статуса (при заданном теге — только
 * с ним), пропускает тех, кому этот бот уже уходил, остальным запускает бота и пишет журнал
 * (тип {@link OrderType#MANUAL}, позиция 0). Ход и итоги видны через {@link ManualRun}.
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
            List<Long> leads = run.getTagName() == null
                    ? amoCrmGateway.getLeadIdsByStatus(run.getPipelineId(), run.getStatusId())
                    : amoCrmGateway.getLeadIdsByStatusAndTag(run.getPipelineId(), run.getStatusId(), run.getTagName());
            run.markTotal(leads.size());
            BotStep step = new BotStep(run.getBotId(), 0);
            for (Long leadId : leads) {
                try {
                    if (executionReader.alreadyExecuted(leadId, run.getBotId())) {
                        run.incSkipped();
                        continue;
                    }
                    if (amoCrmGateway.runSalesbot(leadId, run.getBotId())) {
                        executionRecorder.recordSuccess(leadId, OrderType.MANUAL, step);
                        run.incSent();
                    } else {
                        executionRecorder.recordFailed(leadId, OrderType.MANUAL, step);
                        run.incFailed();
                    }
                } catch (Exception e) {
                    run.incFailed();
                    log.error("Manual batch #{}: failed lead {} bot {}", run.getId(), leadId, run.getBotId(), e);
                }
            }
            run.finish(Instant.now());
            log.info("Manual batch #{} done: pipeline={} status={} bot={} tag={} -> sent={}, skipped(already)={}, failed={}, total={}",
                    run.getId(), run.getPipelineId(), run.getStatusId(), run.getBotId(), run.getTagName(),
                    run.getSent(), run.getSkipped(), run.getFailed(), leads.size());
        } catch (Exception e) {
            run.fail(Instant.now(), e.getMessage() == null ? e.toString() : e.getMessage());
            log.error("Manual batch #{} failed: pipeline={} status={} bot={}",
                    run.getId(), run.getPipelineId(), run.getStatusId(), run.getBotId(), e);
        }
    }
}
