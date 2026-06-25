package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.OrderType;
import ru.anyforms.repository.BotExecutionLogRepository;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotExecutionRecorder;
import ru.anyforms.service.salesbot.BotStep;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Адаптер лога: реализует чтение ({@link BotExecutionReader}) и запись
 * ({@link BotExecutionRecorder}) поверх {@link BotExecutionLogRepository}.
 * Запись идемпотентна по {@code (lead_id, bot_id)} (upsert).
 */
@Slf4j
@Component
@AllArgsConstructor
class BotExecutionLogStore implements BotExecutionReader, BotExecutionRecorder {

    private final BotExecutionLogRepository repository;

    @Override
    public Set<Integer> successPositions(Long leadId, OrderType type) {
        return Set.copyOf(repository.findSuccessPositions(leadId, type));
    }

    @Override
    public boolean alreadySentToday(Long leadId, Instant now) {
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS); // UTC-полночь текущих суток
        return repository.existsByLeadIdAndStatusAndDateExecutedGreaterThanEqual(
                leadId, BotExecutionStatus.SUCCESS, dayStart);
    }

    @Override
    public boolean alreadyExecuted(Long leadId, Long botId) {
        return repository.existsByLeadIdAndBotIdAndStatus(leadId, botId, BotExecutionStatus.SUCCESS);
    }

    @Override
    @Transactional
    public void recordSuccess(Long leadId, OrderType type, BotStep step) {
        upsert(leadId, type, step, BotExecutionStatus.SUCCESS);
    }

    @Override
    @Transactional
    public void recordFailed(Long leadId, OrderType type, BotStep step) {
        upsert(leadId, type, step, BotExecutionStatus.FAILED);
    }

    private void upsert(Long leadId, OrderType type, BotStep step, BotExecutionStatus status) {
        repository.upsert(
                leadId,
                step.botId(),
                step.position(),
                type.name(),
                status.name(),
                Instant.now());
        log.debug("bot_execution_log upsert: lead={} bot={} pos={} type={} status={}",
                leadId, step.botId(), step.position(), type, status);
    }
}
