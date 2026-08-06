package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.integration.AmoCrmGateway;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseAmoLeadServiceImplTest {

    private static final long EDUCATION_PIPELINE_ID = 10863606L;
    private static final long REALIZED_STATUS_ID = 142L;
    private static final long CONTACT_ID = 777L;

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
    void failsWhenContactNotFound() {
        when(amoCrmGateway.findContactIdByQuery(anyString())).thenReturn(null);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.pushCoursePurchase(transaction("COURSE", 990000L)));

        assertTrue(e.getMessage().contains("не найден"));
        verify(amoCrmGateway, never()).getLeadIdsByContact(anyLong());
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong());
    }

    @Test
    void failsWhenNoActiveLeadInEducationPipeline() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(CONTACT_ID);
        when(amoCrmGateway.getLeadIdsByContact(CONTACT_ID)).thenReturn(List.of(10L, 20L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, REALIZED_STATUS_ID));
        when(amoCrmGateway.getLead(20L)).thenReturn(lead(9999999L, 85479838L));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.pushCoursePurchase(transaction("COURSE", 990000L)));

        assertTrue(e.getMessage().contains("нет активной сделки"));
        verify(amoCrmGateway, never()).updateLeadFields(anyLong(), anyLong(), any());
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong());
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
