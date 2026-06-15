package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.service.salesbot.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Реализация одного прогона дрип-кампании. Зависит только от портов (DIP):
 * ничего не знает про amoCRM/БД напрямую.
 */
@Slf4j
@Component
@AllArgsConstructor
class DripCampaignRunnerImpl implements DripCampaignRunner {

    private final OrderTypeFunnelDirectory funnelDirectory;
    private final LeadProvider leadProvider;
    private final NextBotResolver nextBotResolver;
    private final LeadStatusVerifier leadStatusVerifier;
    private final SalesbotTrigger salesbotTrigger;
    private final BotExecutionRecorder executionRecorder;
    private final BotExecutionReader executionReader;

    @Override
    public void runOnce() {
        Instant now = Instant.now();
        List<OrderType> types = funnelDirectory.configuredTypes();
        log.info("Drip campaign run started for {} type(s): {}", types.size(), types);

        for (OrderType type : types) {
            try {
                processType(type, now);
            } catch (Exception e) {
                log.error("Drip campaign failed for type {}", type, e);
            }
        }
        log.info("Drip campaign run finished");
    }

    private void processType(OrderType type, Instant now) {
        Optional<FunnelTarget> target = funnelDirectory.targetFor(type);
        if (target.isEmpty()) {
            log.warn("No funnel configured for type {}, skipping", type);
            return;
        }
        FunnelTarget funnel = target.get();

        // Запрос №1: лиды в целевом статусе.
        List<Long> leads = leadProvider.leadsInStatus(funnel);
        log.info("Type {}: {} lead(s) in target status", type, leads.size());

        for (Long leadId : leads) {
            try {
                processLead(type, funnel, leadId, now);
            } catch (Exception e) {
                log.error("Failed to process lead {} (type {})", leadId, type, e);
            }
            // TODO(лимиты amoCRM ~7 req/sec): добавить троттлинг/паузу между лидами,
            // т.к. на каждого лида приходится ≥2 запроса (перечитать статус + запустить бота).
        }
    }

    private void processLead(OrderType type, FunnelTarget funnel, Long leadId, Instant now) {
        // Дневной guard: если лиду сегодня уже успешно отправляли бота — не шлём повторно
        // (защита от случайного двойного прогона за сутки: рестарт/catch-up). Только по нашей БД.
        if (executionReader.alreadySentToday(leadId, now)) {
            log.info("Lead {} already received a bot today — skipping (per-day guard)", leadId);
            return;
        }

        Optional<BotStep> next = nextBotResolver.nextBot(type, leadId);
        if (next.isEmpty()) {
            // Вся цепочка уже отработала — ничего не делаем, лид остаётся.
            return;
        }
        BotStep step = next.get();

        // Перечитываем актуальный статус: лид мог выйти из статуса между запросом №1 и сейчас.
        if (!leadStatusVerifier.isInTargetStatus(leadId, funnel)) {
            log.info("Lead {} left target status before bot {} (pos {}); recording FAILED, skipping",
                    leadId, step.botId(), step.position());
            executionRecorder.recordFailed(leadId, type, step);
            return;
        }

        // Запрос №2: запуск бота (fire-and-forget, без ретраев).
        boolean ok = salesbotTrigger.run(leadId, step.botId());
        if (ok) {
            executionRecorder.recordSuccess(leadId, type, step);
        } else {
            executionRecorder.recordFailed(leadId, type, step);
        }
    }
}
