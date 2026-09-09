package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.service.salesbot.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Реализация одного прогона дрип-кампании по группам. Зависит только от портов (DIP):
 * ничего не знает про amoCRM/БД напрямую. Ритм задают задержки шагов: следующий бот уходит
 * не раньше чем через {@link BotStep#delayMinutes()} после предыдущего (для первого — после
 * того, как сделка впервые замечена в статусе группы).
 * <p>
 * Троттлинг под лимит amoCRM здесь НЕ делается — он живёт в самом {@link ru.anyforms.integration.AmoCrmGateway}
 * (rate-limited декоратор), поэтому бизнес-логика просто зовёт порты последовательно.
 */
@Slf4j
@Component
@AllArgsConstructor
class DripCampaignRunnerImpl implements DripCampaignRunner {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private final BotGroupDirectory groupDirectory;
    private final LeadProvider leadProvider;
    private final NextBotResolver nextBotResolver;
    private final LeadStatusVerifier leadStatusVerifier;
    private final SalesbotTrigger salesbotTrigger;
    private final BotExecutionRecorder executionRecorder;
    private final BotExecutionReader executionReader;
    private final LeadSeenStore leadSeenStore;

    @Override
    public void runOnce() {
        Instant now = Instant.now();
        List<ActiveGroup> groups = groupDirectory.activeGroups();
        log.info("Drip campaign run started for {} group(s): {}", groups.size(),
                groups.stream().map(ActiveGroup::name).toList());

        for (ActiveGroup group : groups) {
            try {
                processGroup(group, now);
            } catch (Exception e) {
                log.error("Drip campaign failed for group {} ({})", group.id(), group.name(), e);
            }
        }
        log.info("Drip campaign run finished");
    }

    private void processGroup(ActiveGroup group, Instant now) {
        // Запрос №1: лиды в целевом статусе (с пагинацией — все, не только первые 250).
        List<Long> leads = leadProvider.leadsInStatus(group.target());
        log.info("Group {} ({}): {} lead(s) in target status", group.id(), group.name(), leads.size());

        for (Long leadId : leads) {
            try {
                processLead(group, leadId, now);
            } catch (Exception e) {
                log.error("Failed to process lead {} (group {})", leadId, group.id(), e);
            }
        }
    }

    private void processLead(ActiveGroup group, Long leadId, Instant now) {
        // Якорь первого шага: когда сделку впервые увидели в статусе группы (пишется один раз).
        Instant firstSeen = leadSeenStore.firstSeenOrRecord(group.id(), leadId, now);

        Optional<BotStep> next = nextBotResolver.nextBot(group.id(), leadId);
        if (next.isEmpty()) {
            // Вся цепочка уже отработала — ничего не делаем, лид остаётся.
            return;
        }
        BotStep step = next.get();

        // Этот бот лиду уже уходил (цепочку переставили/поменяли бота на пройденной позиции):
        // второй раз не шлём, но позицию засчитываем, чтобы цепочка шла дальше.
        if (executionReader.alreadyExecuted(leadId, step.botId())) {
            log.info("Lead {} already received bot {} earlier; marking position {} done without sending",
                    leadId, step.botId(), step.position());
            executionRecorder.recordGroupSuccess(leadId, group.id(), step);
            return;
        }

        // Задержка шага: от предыдущей успешной отправки в группе, для первого шага — от якоря.
        Instant anchor = executionReader.lastSuccessAt(leadId, group.id()).orElse(firstSeen);
        Instant readyAt = anchor.plus(Duration.ofMinutes(step.delayMinutes() == null ? 0 : step.delayMinutes()));
        if (now.isBefore(readyAt)) {
            log.debug("Lead {}: step {} (bot {}) not due until {} — waiting", leadId, step.position(), step.botId(), readyAt);
            return;
        }
        // Окно отправки группы: задержка истекла ночью — ждём следующего открытия окна.
        if (!group.window().contains(now.atZone(MSK).toLocalTime())) {
            log.debug("Lead {}: step {} is due but outside send window {} of group {} — waiting",
                    leadId, step.position(), group.window(), group.id());
            return;
        }

        // Перечитываем актуальный статус: лид мог выйти из статуса между запросом №1 и сейчас.
        if (!leadStatusVerifier.isInTargetStatus(leadId, group.target())) {
            log.info("Lead {} left target status before bot {} (pos {}); recording FAILED, skipping",
                    leadId, step.botId(), step.position());
            executionRecorder.recordGroupFailed(leadId, group.id(), step);
            return;
        }

        // Запрос №2: запуск бота (fire-and-forget, без ретраев).
        boolean ok = salesbotTrigger.run(leadId, step.botId());
        if (ok) {
            executionRecorder.recordGroupSuccess(leadId, group.id(), step);
        } else {
            executionRecorder.recordGroupFailed(leadId, group.id(), step);
        }
    }
}
