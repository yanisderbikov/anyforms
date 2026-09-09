package ru.anyforms.service.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.amo.LeadFilter;
import ru.anyforms.model.salesbot.BotRunType;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotExecutionRecorder;
import ru.anyforms.service.salesbot.BotStep;
import ru.anyforms.service.salesbot.SalesbotTrigger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Повторные продажи розницы: по будням берёт сделки розничной воронки в статусе
 * «Успешно реализовано», закрытые не позже {@code daysAfterClose} дней назад, и запускает
 * им бота {@code amocrm.bot.retail.repeat.id}. Статус сделки не меняем — это делает сам бот.
 * <p>
 * Раньше задача ({@code OrderMoveToSecond}) переносила сделки в другую воронку. Дедуп — по
 * журналу {@code bot_execution_log} (тип {@link BotRunType#RETAIL_TO_REPEAT}): одной сделке бот
 * уходит один раз, даже если она задержалась в статусе.
 */
@Slf4j
@Component
public class RetailRepeatInviteTask {

    private final AmoCrmGateway amoCrmGateway;
    private final SalesbotTrigger salesbotTrigger;
    private final BotExecutionReader executionReader;
    private final BotExecutionRecorder executionRecorder;
    private final Long retailPipelineId;
    private final Long botId;
    private final int daysAfterClose;

    public RetailRepeatInviteTask(AmoCrmGateway amoCrmGateway,
                                  SalesbotTrigger salesbotTrigger,
                                  BotExecutionReader executionReader,
                                  BotExecutionRecorder executionRecorder,
                                  @Value("${amocrm.retail.pipeline.id}") Long retailPipelineId,
                                  @Value("${amocrm.bot.retail.repeat.id}") Long botId,
                                  @Value("${amocrm.retail.repeat.days-after-close}") int daysAfterClose) {
        this.amoCrmGateway = amoCrmGateway;
        this.salesbotTrigger = salesbotTrigger;
        this.executionReader = executionReader;
        this.executionRecorder = executionRecorder;
        this.retailPipelineId = retailPipelineId;
        this.botId = botId;
        this.daysAfterClose = daysAfterClose;
    }

    /** По будням в 10:53 МСК. */
    @Scheduled(cron = "0 53 10 ? * MON-FRI", zone = "Europe/Moscow")
    public void process() {
        try {
            runOnce(Instant.now());
        } catch (Exception e) {
            log.error("Retail repeat invite task failed", e);
        }
    }

    /** Один прогон; вынесен для тестов. */
    public void runOnce(Instant now) {
        long closedBefore = now.minus(Duration.ofDays(daysAfterClose)).getEpochSecond();
        List<Long> leads = amoCrmGateway.getLeadIdsByStatus(retailPipelineId,
                AmoLeadStatus.REALIZED.getStatusId(), LeadFilter.closedBefore(closedBefore));
        BotStep step = new BotStep(botId, 1);
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (Long leadId : leads) {
            try {
                if (executionReader.alreadyExecuted(leadId, botId)) {
                    skipped++;
                    continue;
                }
                if (salesbotTrigger.run(leadId, botId)) {
                    executionRecorder.recordSuccess(leadId, BotRunType.RETAIL_TO_REPEAT, step);
                    sent++;
                } else {
                    executionRecorder.recordFailed(leadId, BotRunType.RETAIL_TO_REPEAT, step);
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Retail repeat invite: failed lead {} bot {}", leadId, botId, e);
            }
        }
        log.info("Retail repeat invite: pipeline={} closedBefore={} bot={} -> sent={}, skipped(already)={}, failed={}, total={}",
                retailPipelineId, closedBefore, botId, sent, skipped, failed, leads.size());
    }
}
