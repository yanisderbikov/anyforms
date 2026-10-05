package ru.anyforms.service.amo.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.amo.AmoReplyCheckTaskPayload;
import ru.anyforms.integration.AmoChatGateway;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoChatMessage;
import ru.anyforms.model.amo.AmoPipeline;
import ru.anyforms.model.amo.AmoTalk;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.service.amo.MissedReplyChecker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
class MissedReplyCheckerImpl implements MissedReplyChecker {

    private static final long CLOCK_SKEW_SECONDS = 60;

    private final AmoCrmGateway amoCrmGateway;
    private final AmoChatGateway amoChatGateway;
    private final int timeoutMinutes;

    MissedReplyCheckerImpl(AmoCrmGateway amoCrmGateway,
                           AmoChatGateway amoChatGateway,
                           @Value("${amocrm.reply.timeout-minutes}") int timeoutMinutes) {
        this.amoCrmGateway = amoCrmGateway;
        this.amoChatGateway = amoChatGateway;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Override
    public void check(AmoReplyCheckTaskPayload payload) {
        Long leadId = payload.getLeadId();
        if (leadId == null) {
            throw new IllegalStateException("В таске нет leadId");
        }
        var lead = amoCrmGateway.getLead(leadId);
        if (lead == null || AmoPipeline.TRASH.getPipelineId().equals(lead.getPipelineId())) {
            return;
        }
        Instant deadline = Instant.now().minusSeconds(timeoutMinutes * 60L - CLOCK_SKEW_SECONDS);
        if (!hasOverdueUnanswered(payload, deadline)) {
            return;
        }
        if (amoCrmGateway.hasIncompleteTask(leadId, AmoTaskId.LOST_MESSAGE.getTaskId())) {
            return;
        }
        amoCrmGateway.setNewTask(
                lead.getResponsibleUserId(),
                AmoTaskId.LOST_MESSAGE.getTaskId(),
                "Пропущенное: ответ более " + timeoutMinutes + " минут",
                leadId,
                0
        );
        log.info("Нет ответа клиенту по сделке {} дольше {} минут — поставлена задача", leadId, timeoutMinutes);
    }

    private boolean hasOverdueUnanswered(AmoReplyCheckTaskPayload payload, Instant deadline) {
        List<String> chatIds = payload.getChatId() != null
                ? List.of(payload.getChatId())
                : amoChatGateway.getLeadTalks(payload.getLeadId(), true).stream().map(AmoTalk::chatId).toList();
        for (String chatId : chatIds) {
            Optional<Instant> waitingSince = firstUnansweredAt(amoChatGateway.getChatMessages(chatId).messages());
            if (waitingSince.isPresent() && !waitingSince.get().isAfter(deadline)) {
                return true;
            }
        }
        return false;
    }

    static Optional<Instant> firstUnansweredAt(List<AmoChatMessage> chronological) {
        Instant firstIncoming = null;
        for (AmoChatMessage m : chronological) {
            if (m.direction() == AmoChatMessage.Direction.OUT) {
                firstIncoming = null;
            } else if (firstIncoming == null && m.createdAt() != null) {
                firstIncoming = m.createdAt();
            }
        }
        return Optional.ofNullable(firstIncoming);
    }
}
