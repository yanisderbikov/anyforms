package ru.anyforms.service.amo.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.amo.AmoReplyCheckTaskPayload;
import ru.anyforms.integration.AmoChatGateway;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoChatMessage;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;
import ru.anyforms.model.amo.AmoPipeline;
import ru.anyforms.model.amo.AmoTalk;
import ru.anyforms.model.amo.AmoTaskId;
import ru.anyforms.service.amo.MissedReplyChecker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        log.info("Проверка пропущенного ответа: сделка {}, контакт {}, чат {}, таймаут {} мин",
                leadId, payload.getContactId(), payload.getChatId(), timeoutMinutes);
        List<AmoLead> openLeads = openLeadsOfContact(payload);
        if (openLeads.isEmpty()) {
            log.info("Проверка пропущенного ответа: сделка {} — у контакта нет открытых сделок, пропуск", leadId);
            return;
        }
        Instant deadline = Instant.now().minusSeconds(timeoutMinutes * 60L - CLOCK_SKEW_SECONDS);
        if (!hasOverdueUnanswered(payload, deadline)) {
            log.info("Проверка пропущенного ответа: сделка {} — просроченных неотвеченных сообщений нет, задача не нужна", leadId);
            return;
        }
        for (AmoLead lead : openLeads) {
            if (amoCrmGateway.hasIncompleteTask(lead.getId(), AmoTaskId.LOST_MESSAGE.getTaskId())) {
                log.info("Проверка пропущенного ответа: по сделке {} уже есть незакрытая задача «Пропущенное», дубль не ставим", lead.getId());
                continue;
            }
            amoCrmGateway.setNewTask(
                    lead.getResponsibleUserId(),
                    AmoTaskId.LOST_MESSAGE.getTaskId(),
                    "Пропущенное: ответ более " + timeoutMinutes + " минут",
                    lead.getId(),
                    0
            );
            log.info("Нет ответа клиенту дольше {} минут — по сделке {} поставлена задача ответственному {}",
                    timeoutMinutes, lead.getId(), lead.getResponsibleUserId());
        }
    }

    private List<AmoLead> openLeadsOfContact(AmoReplyCheckTaskPayload payload) {
        Long leadId = payload.getLeadId();
        Long contactId = payload.getContactId() != null ? payload.getContactId() : amoCrmGateway.getContactIdFromLead(leadId);
        Set<Long> leadIds = new LinkedHashSet<>();
        leadIds.add(leadId);
        if (contactId != null) {
            List<Long> contactLeadIds = amoCrmGateway.getLeadIdsByContact(contactId);
            leadIds.addAll(contactLeadIds);
            log.info("Проверка пропущенного ответа: у контакта {} сделок: {} {}", contactId, contactLeadIds.size(), contactLeadIds);
        } else {
            log.warn("Проверка пропущенного ответа: у сделки {} не найден контакт, проверяем только её", leadId);
        }
        List<AmoLead> open = new ArrayList<>();
        for (Long id : leadIds) {
            AmoLead lead = amoCrmGateway.getLead(id);
            if (lead == null) {
                log.warn("Проверка пропущенного ответа: сделка {} не найдена в amoCRM, пропуск", id);
                continue;
            }
            if (AmoPipeline.TRASH.getPipelineId().equals(lead.getPipelineId())) {
                log.info("Проверка пропущенного ответа: сделка {} в воронке «Мусор», пропуск", id);
                continue;
            }
            if (isClosed(lead)) {
                log.info("Проверка пропущенного ответа: сделка {} закрыта (статус {}), пропуск", id, lead.getStatusId());
                continue;
            }
            open.add(lead);
        }
        log.info("Проверка пропущенного ответа: открытых сделок {}: {}", open.size(), open.stream().map(AmoLead::getId).toList());
        return open;
    }

    private static boolean isClosed(AmoLead lead) {
        Long statusId = lead.getStatusId();
        return AmoLeadStatus.REALIZED.getStatusId().equals(statusId)
                || AmoLeadStatus.NOT_REALIZED.getStatusId().equals(statusId);
    }

    private boolean hasOverdueUnanswered(AmoReplyCheckTaskPayload payload, Instant deadline) {
        Long leadId = payload.getLeadId();
        List<String> chatIds;
        if (payload.getChatId() != null) {
            chatIds = List.of(payload.getChatId());
        } else {
            chatIds = amoChatGateway.getLeadTalks(leadId, true).stream().map(AmoTalk::chatId).toList();
            log.info("Проверка пропущенного ответа: в таске нет chatId, по сделке {} найдено чатов: {}", leadId, chatIds.size());
        }
        for (String chatId : chatIds) {
            List<AmoChatMessage> messages = amoChatGateway.getChatMessages(chatId).messages();
            Optional<Instant> waitingSince = firstUnansweredAt(messages);
            if (waitingSince.isEmpty()) {
                log.info("Проверка пропущенного ответа: сделка {}, чат {} — сообщений {}, последнее слово за менеджером", leadId, chatId, messages.size());
                continue;
            }
            long waitingMinutes = Duration.between(waitingSince.get(), Instant.now()).toMinutes();
            if (!waitingSince.get().isAfter(deadline)) {
                log.info("Проверка пропущенного ответа: сделка {}, чат {} — клиент ждёт с {} ({} мин), просрочено", leadId, chatId, waitingSince.get(), waitingMinutes);
                return true;
            }
            log.info("Проверка пропущенного ответа: сделка {}, чат {} — клиент ждёт с {} ({} мин), таймаут ещё не вышел", leadId, chatId, waitingSince.get(), waitingMinutes);
        }
        return false;
    }

    static Optional<Instant> firstUnansweredAt(List<AmoChatMessage> chronological) {
        Instant firstIncoming = null;
        for (AmoChatMessage m : chronological) {
            if (m.error() != null) {
                continue;
            }
            if (m.direction() == AmoChatMessage.Direction.OUT) {
                firstIncoming = null;
            } else if (firstIncoming == null && m.createdAt() != null) {
                firstIncoming = m.createdAt();
            }
        }
        return Optional.ofNullable(firstIncoming);
    }
}
