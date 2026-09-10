package ru.anyforms.service.salesbot.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.model.salesbot.BotExecutionStatus;
import ru.anyforms.model.salesbot.BotRunType;
import ru.anyforms.repository.BotExecutionLogRepository;
import ru.anyforms.service.salesbot.BotExecutionReader;
import ru.anyforms.service.salesbot.BotExecutionRecorder;
import ru.anyforms.service.salesbot.BotStep;

import java.time.Instant;
import java.util.Optional;
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
    public Set<Integer> successPositions(Long leadId, Long groupId) {
        return Set.copyOf(repository.findSuccessPositions(leadId, groupId));
    }

    @Override
    public Optional<Instant> lastSuccessAt(Long leadId, Long groupId) {
        return Optional.ofNullable(repository.findLastSuccessAt(leadId, groupId));
    }

    @Override
    public boolean alreadyExecuted(Long leadId, Long botId) {
        return repository.existsByLeadIdAndBotIdAndStatus(leadId, botId, BotExecutionStatus.SUCCESS);
    }

    @Override
    @Transactional
    public void recordGroupSuccess(Long leadId, Long groupId, BotStep step) {
        upsert(leadId, BotRunType.DRIP, groupId, step, BotExecutionStatus.SUCCESS);
    }

    @Override
    @Transactional
    public void recordGroupFailed(Long leadId, Long groupId, BotStep step) {
        upsert(leadId, BotRunType.DRIP, groupId, step, BotExecutionStatus.FAILED);
    }

    @Override
    @Transactional
    public void recordSuccess(Long leadId, BotRunType type, BotStep step) {
        upsert(leadId, type, null, step, BotExecutionStatus.SUCCESS);
    }

    @Override
    @Transactional
    public void recordFailed(Long leadId, BotRunType type, BotStep step) {
        upsert(leadId, type, null, step, BotExecutionStatus.FAILED);
    }

    private void upsert(Long leadId, BotRunType type, Long groupId, BotStep step, BotExecutionStatus status) {
        repository.upsert(leadId, step.botId(), step.position(), type.name(), groupId, status.name(), Instant.now());
        log.debug("bot_execution_log upsert: lead={} bot={} pos={} type={} group={} status={}",
                leadId, step.botId(), step.position(), type, groupId, status);
    }
}
