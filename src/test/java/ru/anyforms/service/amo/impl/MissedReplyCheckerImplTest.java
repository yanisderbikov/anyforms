package ru.anyforms.service.amo.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.amo.AmoReplyCheckTaskPayload;
import ru.anyforms.integration.AmoChatGateway;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoChatMessage;
import ru.anyforms.model.amo.AmoChatMessages;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.amo.AmoLeadStatus;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissedReplyCheckerImplTest {

    private static final long LEAD_ID = 100L;
    private static final long SECOND_LEAD_ID = 200L;
    private static final long CONTACT_ID = 777L;
    private static final long RESPONSIBLE = 555L;
    private static final long SECOND_RESPONSIBLE = 666L;
    private static final String TASK_TEXT = "Пропущенное: ответ более 10 минут";

    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final AmoChatGateway amoChatGateway = mock(AmoChatGateway.class);
    private final MissedReplyCheckerImpl checker = new MissedReplyCheckerImpl(amoCrmGateway, amoChatGateway, 10);

    private final AmoReplyCheckTaskPayload payload = new AmoReplyCheckTaskPayload(LEAD_ID, "chat-1", CONTACT_ID);

    private AmoLead lead(long id, long pipelineId, long responsible) {
        AmoLead lead = new AmoLead();
        lead.setId(id);
        lead.setPipelineId(pipelineId);
        lead.setResponsibleUserId(responsible);
        when(amoCrmGateway.getLead(id)).thenReturn(lead);
        return lead;
    }

    private void leadInPipeline(long pipelineId) {
        lead(LEAD_ID, pipelineId, RESPONSIBLE);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(LEAD_ID));
    }

    private void contactLeads(Long... ids) {
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(ids));
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
        return msg(minutesAgo, direction, null);
    }

    private static AmoChatMessage failedOut(int minutesAgo) {
        return msg(minutesAgo, AmoChatMessage.Direction.OUT, "4: Message was not delivered");
    }

    private static AmoChatMessage msg(int minutesAgo, AmoChatMessage.Direction direction, String error) {
        return new AmoChatMessage("m" + minutesAgo + direction, Instant.now().minusSeconds(minutesAgo * 60L), direction, null,
                direction == AmoChatMessage.Direction.OUT ? AmoChatMessage.AuthorType.MANAGER : AmoChatMessage.AuthorType.CLIENT,
                "text", "", null, null, null, error);
    }

    private void verifyNoTask() {
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    private void verifyTask(long leadId, long responsible) {
        verify(amoCrmGateway).setNewTask(eq(responsible), eq(AmoTaskId.LOST_MESSAGE.getTaskId()), eq(TASK_TEXT), eq(leadId), eq(0));
    }

    @Test
    void createsMissedTaskWhenClientWaitsLongerThanTimeout() {
        leadInPipeline(1L);
        chat(out(30), in(12), in(11));

        checker.check(payload);

        verifyTask(LEAD_ID, RESPONSIBLE);
    }

    @Test
    void undeliveredReplyDoesNotCountAsAnswer() {
        leadInPipeline(1L);
        chat(in(15), failedOut(14));

        checker.check(payload);

        verifyTask(LEAD_ID, RESPONSIBLE);
    }

    @Test
    void deliveredReplyAfterFailedOneCountsAsAnswer() {
        leadInPipeline(1L);
        chat(in(15), failedOut(14), out(13));

        checker.check(payload);

        verifyNoTask();
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
    void createsTaskInEveryOpenLeadOfContactForItsOwnResponsible() {
        lead(LEAD_ID, AmoPipeline.MAIN.getPipelineId(), RESPONSIBLE);
        lead(SECOND_LEAD_ID, AmoPipeline.RETAIL.getPipelineId(), SECOND_RESPONSIBLE);
        contactLeads(LEAD_ID, SECOND_LEAD_ID);
        chat(in(15));

        checker.check(payload);

        verifyTask(LEAD_ID, RESPONSIBLE);
        verifyTask(SECOND_LEAD_ID, SECOND_RESPONSIBLE);
        verify(amoChatGateway, times(1)).getChatMessages("chat-1");
    }

    @Test
    void createsTaskInEachLeadEvenWhenSameManagerIsResponsible() {
        lead(LEAD_ID, AmoPipeline.MAIN.getPipelineId(), RESPONSIBLE);
        lead(SECOND_LEAD_ID, AmoPipeline.RETAIL.getPipelineId(), RESPONSIBLE);
        contactLeads(LEAD_ID, SECOND_LEAD_ID);
        chat(in(15));

        checker.check(payload);

        verifyTask(LEAD_ID, RESPONSIBLE);
        verifyTask(SECOND_LEAD_ID, RESPONSIBLE);
    }

    @Test
    void skipsContactLeadsInTrashOrClosed() {
        lead(LEAD_ID, AmoPipeline.MAIN.getPipelineId(), RESPONSIBLE);
        lead(SECOND_LEAD_ID, AmoPipeline.TRASH.getPipelineId(), SECOND_RESPONSIBLE);
        AmoLead closed = lead(300L, AmoPipeline.RETAIL.getPipelineId(), SECOND_RESPONSIBLE);
        closed.setStatusId(AmoLeadStatus.REALIZED.getStatusId());
        contactLeads(LEAD_ID, SECOND_LEAD_ID, 300L);
        chat(in(15));

        checker.check(payload);

        verifyTask(LEAD_ID, RESPONSIBLE);
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), eq(SECOND_LEAD_ID), anyInt());
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), eq(300L), anyInt());
    }

    @Test
    void stillNotifiesOtherOpenLeadWhenWebhookLeadWentToTrash() {
        lead(LEAD_ID, AmoPipeline.TRASH.getPipelineId(), RESPONSIBLE);
        lead(SECOND_LEAD_ID, AmoPipeline.RETAIL.getPipelineId(), SECOND_RESPONSIBLE);
        contactLeads(LEAD_ID, SECOND_LEAD_ID);
        chat(in(15));

        checker.check(payload);

        verifyTask(SECOND_LEAD_ID, SECOND_RESPONSIBLE);
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), eq(LEAD_ID), anyInt());
    }

    @Test
    void duplicateCheckIsPerLead() {
        lead(LEAD_ID, AmoPipeline.MAIN.getPipelineId(), RESPONSIBLE);
        lead(SECOND_LEAD_ID, AmoPipeline.RETAIL.getPipelineId(), SECOND_RESPONSIBLE);
        contactLeads(LEAD_ID, SECOND_LEAD_ID);
        when(amoCrmGateway.hasIncompleteTask(LEAD_ID, AmoTaskId.LOST_MESSAGE.getTaskId())).thenReturn(true);
        chat(in(15));

        checker.check(payload);

        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), eq(LEAD_ID), anyInt());
        verifyTask(SECOND_LEAD_ID, SECOND_RESPONSIBLE);
    }

    @Test
    void resolvesContactFromLeadWhenPayloadHasNoContact() {
        lead(LEAD_ID, AmoPipeline.MAIN.getPipelineId(), RESPONSIBLE);
        lead(SECOND_LEAD_ID, AmoPipeline.RETAIL.getPipelineId(), SECOND_RESPONSIBLE);
        when(amoCrmGateway.getContactIdFromLead(LEAD_ID)).thenReturn(CONTACT_ID);
        contactLeads(LEAD_ID, SECOND_LEAD_ID);
        chat(in(15));

        checker.check(new AmoReplyCheckTaskPayload(LEAD_ID, "chat-1", null));

        verifyTask(LEAD_ID, RESPONSIBLE);
        verifyTask(SECOND_LEAD_ID, SECOND_RESPONSIBLE);
    }

    @Test
    void firstUnansweredResetsOnEveryOutgoing() {
        AmoChatMessage second = in(5);

        assertEquals(second.createdAt(), MissedReplyCheckerImpl.firstUnansweredAt(List.of(in(10), out(6), second, in(3))).orElseThrow());
        assertTrue(MissedReplyCheckerImpl.firstUnansweredAt(List.of(in(10), out(6))).isEmpty());
    }
}
