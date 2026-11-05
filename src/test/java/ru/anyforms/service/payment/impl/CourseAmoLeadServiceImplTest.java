package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoContact;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.payment.PaymentTransaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseAmoLeadServiceImplTest {

    private static final long EDUCATION_PIPELINE_ID = 10863606L;
    private static final long REALIZED_STATUS_ID = 142L;
    private static final long CONTACT_ID = 777L;
    private static final long IRINA_ID = 13161462L;

    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final EducationLeadFinder educationLeadFinder = new EducationLeadFinder(amoCrmGateway);
    private final CourseAmoLeadServiceImpl service =
            new CourseAmoLeadServiceImpl(amoCrmGateway, new AmoContactFinder(amoCrmGateway), educationLeadFinder);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "educationPipelineId", EDUCATION_PIPELINE_ID);
        ReflectionTestUtils.setField(educationLeadFinder, "educationPipelineId", EDUCATION_PIPELINE_ID);
    }

    private static PaymentTransaction transaction(String productCode, Long amountKopecks) {
        return PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .productCode(productCode)
                .email("buyer@mail.ru")
                .contactPhone("+79001234567")
                .amount(amountKopecks)
                .build();
    }

    private static AmoLead lead(Long pipelineId, Long statusId) {
        AmoLead lead = new AmoLead();
        lead.setPipelineId(pipelineId);
        lead.setStatusId(statusId);
        return lead;
    }

    private static AmoLead leadWithResponsible(Long responsibleUserId) {
        AmoLead lead = lead(EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID);
        lead.setResponsibleUserId(responsibleUserId);
        return lead;
    }

    private static AmoContact contact(Long responsibleUserId) {
        AmoContact contact = new AmoContact();
        contact.setId(CONTACT_ID);
        contact.setResponsibleUserId(responsibleUserId);
        return contact;
    }

    private void amoUpdatesSucceed() {
        when(amoCrmGateway.updateLeadFields(anyLong(), any(), any())).thenReturn(true);
        when(amoCrmGateway.addTagToLead(anyLong(), anyString())).thenReturn(true);
        when(amoCrmGateway.addNoteToLead(anyLong(), anyString())).thenReturn(true);
        when(amoCrmGateway.updateLeadStatus(anyLong(), anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    void closesActiveEducationLeadWithBudgetTagAndNote() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L, 20L, 30L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 85479838L));
        when(amoCrmGateway.getLead(20L)).thenReturn(lead(9999999L, 85479838L));
        when(amoCrmGateway.getLead(30L)).thenReturn(lead(EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID));
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE", 990000L));

        InOrder inOrder = inOrder(amoCrmGateway);
        inOrder.verify(amoCrmGateway).updateLeadFields(10L, 9900L, Map.of());
        inOrder.verify(amoCrmGateway).addTagToLead(10L, CourseAmoLeadServiceImpl.TAG);
        inOrder.verify(amoCrmGateway).addNoteToLead(10L,
                "Курс куплен на сумму 9900.00 ₽ (COURSE, почта buyer@mail.ru)");
        inOrder.verify(amoCrmGateway).updateLeadStatus(10L, REALIZED_STATUS_ID, EDUCATION_PIPELINE_ID);
    }

    @Test
    void picksLatestActiveLeadWhenSeveralInEducationPipeline() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L, 40L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 85479838L));
        when(amoCrmGateway.getLead(40L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 85479838L));
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE_PERSONAL", 1500000L));

        verify(amoCrmGateway).updateLeadStatus(40L, REALIZED_STATUS_ID, EDUCATION_PIPELINE_ID);
        verify(amoCrmGateway, never()).updateLeadStatus(10L, REALIZED_STATUS_ID, EDUCATION_PIPELINE_ID);
    }

    @Test
    void findsContactByPhoneDigitsWhenEmailNotFound() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(null);
        when(amoCrmGateway.findContactIdByQuery("79001234567")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 85479838L));
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE", 990000L));

        verify(amoCrmGateway).updateLeadStatus(10L, REALIZED_STATUS_ID, EDUCATION_PIPELINE_ID);
    }

    @Test
    void createsRealizedLeadWhenClientNotFoundInAmo() {
        when(amoCrmGateway.findContactIdByQuery(anyString())).thenReturn(null);
        when(amoCrmGateway.createLead(CourseAmoLeadServiceImpl.LEAD_NAME, "Клиент", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID, null)).thenReturn(50L);
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE", 990000L));

        InOrder inOrder = inOrder(amoCrmGateway);
        inOrder.verify(amoCrmGateway).createLead(CourseAmoLeadServiceImpl.LEAD_NAME, "Клиент", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID, null);
        inOrder.verify(amoCrmGateway).updateLeadFields(50L, 9900L, Map.of());
        inOrder.verify(amoCrmGateway).addTagToLead(50L, CourseAmoLeadServiceImpl.TAG);
        inOrder.verify(amoCrmGateway).addNoteToLead(anyLong(), anyString());
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong());
    }

    @Test
    void createsRealizedLeadWhenNoActiveLeadInEducationPipeline() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L, 20L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID));
        when(amoCrmGateway.getLead(20L)).thenReturn(lead(9999999L, 85479838L));
        when(amoCrmGateway.getContact(CONTACT_ID)).thenReturn(contact(IRINA_ID));
        when(amoCrmGateway.createLead(CourseAmoLeadServiceImpl.LEAD_NAME, "Клиент", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID, IRINA_ID)).thenReturn(50L);
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE", 990000L));

        verify(amoCrmGateway).createLead(CourseAmoLeadServiceImpl.LEAD_NAME, "Клиент", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway, never()).updateContactResponsible(anyLong(), anyLong());
        verify(amoCrmGateway).updateLeadFields(50L, 9900L, Map.of());
        verify(amoCrmGateway).addTagToLead(50L, CourseAmoLeadServiceImpl.TAG);
        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong());
    }

    @Test
    void keepsResponsibleOfExistingLeadAndContactWhenClosingIt() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 85479838L));
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE", 990000L));

        verify(amoCrmGateway).updateLeadStatus(10L, REALIZED_STATUS_ID, EDUCATION_PIPELINE_ID);
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway, never()).updateLeadResponsible(anyLong(), anyLong());
        verify(amoCrmGateway, never()).updateContactResponsible(anyLong(), anyLong());
        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void assignsNewContactToLeadResponsibleWhenClientNotFoundInAmo() {
        when(amoCrmGateway.findContactIdByQuery(anyString())).thenReturn(null);
        when(amoCrmGateway.createLead(CourseAmoLeadServiceImpl.LEAD_NAME, "Клиент", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID, null)).thenReturn(50L);
        when(amoCrmGateway.getLead(50L)).thenReturn(leadWithResponsible(IRINA_ID));
        when(amoCrmGateway.getContactIdFromLead(50L)).thenReturn(CONTACT_ID);
        when(amoCrmGateway.updateContactResponsible(CONTACT_ID, IRINA_ID)).thenReturn(true);
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE", 990000L));

        verify(amoCrmGateway).updateContactResponsible(CONTACT_ID, IRINA_ID);
    }

    @Test
    void doesNothingWhenAmoDisabled() {
        when(amoCrmGateway.findContactIdByQuery(anyString())).thenReturn(null);
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), isNull()))
                .thenReturn(null);

        service.pushCoursePurchase(transaction("COURSE", 990000L));

        verify(amoCrmGateway, never()).updateLeadFields(anyLong(), any(), any());
        verify(amoCrmGateway, never()).addTagToLead(anyLong(), anyString());
        verify(amoCrmGateway, never()).addNoteToLead(anyLong(), anyString());
    }

    @Test
    void failsBeforeStatusChangeWhenBudgetUpdateFails() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 85479838L));
        when(amoCrmGateway.updateLeadFields(anyLong(), anyLong(), any())).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.pushCoursePurchase(transaction("COURSE", 990000L)));

        verify(amoCrmGateway, never()).addTagToLead(anyLong(), anyString());
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong());
    }

    @Test
    void noteMentionsCoursePurchaseDetails() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 85479838L));
        amoUpdatesSucceed();

        service.pushCoursePurchase(transaction("COURSE_PERSONAL", null));

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(amoCrmGateway).addNoteToLead(anyLong(), note.capture());
        assertTrue(note.getValue().startsWith("Курс куплен"));
        assertTrue(note.getValue().contains("COURSE_PERSONAL"));
        assertTrue(note.getValue().contains("buyer@mail.ru"));
    }
}
