package ru.anyforms.service.amo.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.amo.AmoReplyCheckTaskPayload;
import ru.anyforms.integration.AmoChatGateway;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoChatMessage;
import ru.anyforms.model.amo.AmoChatMessages;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoPipeline;
import ru.anyforms.model.amo.AmoTaskId;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissedReplyCheckerImplTest {

    private static final long LEAD_ID = 100L;
    private static final long RESPONSIBLE = 555L;

    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final AmoChatGateway amoChatGateway = mock(AmoChatGateway.class);
    private final MissedReplyCheckerImpl checker = new MissedReplyCheckerImpl(amoCrmGateway, amoChatGateway, 10);

    private final AmoReplyCheckTaskPayload payload = new AmoReplyCheckTaskPayload(LEAD_ID, "chat-1");

    private void leadInPipeline(long pipelineId) {
        AmoLead lead = new AmoLead();
        lead.setId(LEAD_ID);
        lead.setPipelineId(pipelineId);
        lead.setResponsibleUserId(RESPONSIBLE);
        when(amoCrmGateway.getLead(LEAD_ID)).thenReturn(lead);
    }

    private void chat(AmoChatMessage... messages) {
        when(amoChatGateway.getChatMessages("chat-1")).thenReturn(new AmoChatMessages("chat-1", List.of(messages), false));
    }

    private static AmoChatMessage in(int minutesAgo) {
        return msg(minutesAgo, AmoChatMessage.Direction.IN);
    }

    private static AmoChatMessage out(int minutesAgo) {
        return msg(minutesAgo, AmoChatMessage.Direction.OUT);
    }

    private static AmoChatMessage msg(int minutesAgo, AmoChatMessage.Direction direction) {
        return new AmoChatMessage("m" + minutesAgo + direction, Instant.now().minusSeconds(minutesAgo * 60L), direction, null,
                direction == AmoChatMessage.Direction.OUT ? AmoChatMessage.AuthorType.MANAGER : AmoChatMessage.AuthorType.CLIENT,
                "text", "", null, null, null, null);
    }

    private void verifyNoTask() {
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    @Test
    void createsMissedTaskWhenClientWaitsLongerThanTimeout() {
        leadInPipeline(1L);
        chat(out(30), in(12), in(11));

        checker.check(payload);

        verify(amoCrmGateway).setNewTask(eq(RESPONSIBLE), eq(AmoTaskId.LOST_MESSAGE.getTaskId()),
                eq("Пропущенное: ответ более 10 минут"), eq(LEAD_ID), eq(0));
    }

    @Test
    void noTaskWhenEachClientMessageAnsweredInTime() {
        leadInPipeline(1L);
        chat(in(10), out(6), in(5), out(0));

        checker.check(payload);

        verifyNoTask();
    }

    @Test
    void noTaskWhenLatestClientMessageIsStillWithinTimeout() {
        leadInPipeline(1L);
        chat(in(10), out(6), in(5));

        checker.check(payload);

        verifyNoTask();
    }

    @Test
    void noDuplicateWhenMissedTaskAlreadyOpen() {
        leadInPipeline(1L);
        chat(in(15));
        when(amoCrmGateway.hasIncompleteTask(LEAD_ID, AmoTaskId.LOST_MESSAGE.getTaskId())).thenReturn(true);

        checker.check(payload);

        verifyNoTask();
    }

    @Test
    void skipsLeadMovedToTrash() {
        leadInPipeline(AmoPipeline.TRASH.getPipelineId());

        checker.check(payload);

        verify(amoChatGateway, never()).getChatMessages(anyString());
        verifyNoTask();
    }

    @Test
    void firstUnansweredResetsOnEveryOutgoing() {
        AmoChatMessage second = in(5);

        assertEquals(second.createdAt(), MissedReplyCheckerImpl.firstUnansweredAt(List.of(in(10), out(6), second, in(3))).orElseThrow());
        assertTrue(MissedReplyCheckerImpl.firstUnansweredAt(List.of(in(10), out(6))).isEmpty());
    }
}
