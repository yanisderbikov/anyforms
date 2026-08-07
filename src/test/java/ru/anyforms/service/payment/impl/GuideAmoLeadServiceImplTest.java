package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.model.amo.AmoLead;
import ru.anyforms.model.payment.PaymentTransaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideAmoLeadServiceImplTest {

    private static final long EDUCATION_PIPELINE_ID = 10863606L;
    private static final long GUIDE_BOUGHT_STATUS_ID = 85479838L;
    private static final long IRINA_ID = 13161462L;
    private static final long REACH_OUT_TASK_TYPE_ID = 1L;
    private static final int THREE_DAYS_MINUTES = 3 * 24 * 60;

    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final EducationLeadFinder educationLeadFinder = new EducationLeadFinder(amoCrmGateway);
    private final GuideAmoLeadServiceImpl service =
            new GuideAmoLeadServiceImpl(amoCrmGateway, new AmoContactFinder(amoCrmGateway), educationLeadFinder);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "educationPipelineId", EDUCATION_PIPELINE_ID);
        ReflectionTestUtils.setField(service, "guideBoughtStatusId", GUIDE_BOUGHT_STATUS_ID);
        ReflectionTestUtils.setField(educationLeadFinder, "educationPipelineId", EDUCATION_PIPELINE_ID);
    }

    private static PaymentTransaction transaction(String name, String phone, String email) {
        return PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .contactName(name)
                .contactPhone(phone)
                .email(email)
                .build();
    }

    private static AmoLead lead(Long pipelineId, Long statusId) {
        AmoLead lead = new AmoLead();
        lead.setPipelineId(pipelineId);
        lead.setStatusId(statusId);
        return lead;
    }

    private void noExistingContactInAmo() {
        when(amoCrmGateway.findContactIdByQuery(anyString())).thenReturn(null);
    }

    @Test
    void createsLeadWithContactDataAndReachOutTask() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID)).thenReturn(555L);
        when(amoCrmGateway.getContactIdFromLead(555L)).thenReturn(777L);

        service.pushGuidePurchase(transaction("Иванов Иван", "+79001234567", "buyer@mail.ru"));

        verify(amoCrmGateway).createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway).updateContactCustomField(777L,
                Map.of(AmoCrmFieldId.FIO_CONTACT.getId(), "Иванов Иван"));
        verify(amoCrmGateway).setNewTask(IRINA_ID, REACH_OUT_TASK_TYPE_ID,
                GuideAmoLeadServiceImpl.TASK_TEXT, 555L, THREE_DAYS_MINUTES);
    }

    @Test
    void usesFallbackContactNameAndSkipsFioWhenNameMissing() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Клиент", null,
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID)).thenReturn(555L);

        service.pushGuidePurchase(transaction(null, null, "buyer@mail.ru"));

        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
        verify(amoCrmGateway).setNewTask(IRINA_ID, REACH_OUT_TASK_TYPE_ID,
                GuideAmoLeadServiceImpl.TASK_TEXT, 555L, THREE_DAYS_MINUTES);
    }

    @Test
    void skipsFioButCreatesTaskWhenLeadHasNoContact() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(555L);
        when(amoCrmGateway.getContactIdFromLead(555L)).thenReturn(null);

        service.pushGuidePurchase(transaction("Иванов Иван", null, "buyer@mail.ru"));

        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
        verify(amoCrmGateway).setNewTask(IRINA_ID, REACH_OUT_TASK_TYPE_ID,
                GuideAmoLeadServiceImpl.TASK_TEXT, 555L, THREE_DAYS_MINUTES);
    }

    @Test
    void attachesLeadToExistingContactWithoutTouchingContactData() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID)).thenReturn(555L);

        service.pushGuidePurchase(transaction("Иванов Иван", "+79001234567", "buyer@mail.ru"));

        verify(amoCrmGateway).createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
        verify(amoCrmGateway).setNewTask(IRINA_ID, REACH_OUT_TASK_TYPE_ID,
                GuideAmoLeadServiceImpl.TASK_TEXT, 555L, THREE_DAYS_MINUTES);
    }

    @Test
    void findsExistingContactByPhoneDigitsWhenEmailNotFound() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(null);
        when(amoCrmGateway.findContactIdByQuery("79001234567")).thenReturn(777L);
        when(amoCrmGateway.createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+7 (900) 123-45-67",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID)).thenReturn(555L);

        service.pushGuidePurchase(transaction("Иванов Иван", "+7 (900) 123-45-67", "buyer@mail.ru"));

        verify(amoCrmGateway).findContactIdByQuery("79001234567");
        verify(amoCrmGateway).createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+7 (900) 123-45-67",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
    }

    @Test
    void movesExistingActiveLeadToGuideBoughtInsteadOfCreatingNew() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.getLeadIdsByContact(777L)).thenReturn(List.of(10L, 30L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 77900786L));
        when(amoCrmGateway.getLead(30L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 142L));
        when(amoCrmGateway.updateLeadStatus(10L, GUIDE_BOUGHT_STATUS_ID, EDUCATION_PIPELINE_ID, IRINA_ID)).thenReturn(true);

        service.pushGuidePurchase(transaction("Иванов Иван", "+79001234567", "buyer@mail.ru"));

        verify(amoCrmGateway).updateLeadStatus(10L, GUIDE_BOUGHT_STATUS_ID, EDUCATION_PIPELINE_ID, IRINA_ID);
        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway).setNewTask(IRINA_ID, REACH_OUT_TASK_TYPE_ID,
                GuideAmoLeadServiceImpl.TASK_TEXT, 10L, THREE_DAYS_MINUTES);
    }

    @Test
    void createsNewLeadWhenNoActiveLeadInEducationPipeline() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.getLeadIdsByContact(777L)).thenReturn(List.of(30L, 40L));
        when(amoCrmGateway.getLead(30L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 142L));
        when(amoCrmGateway.getLead(40L)).thenReturn(lead(9999999L, 77900786L));
        when(amoCrmGateway.createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID)).thenReturn(555L);

        service.pushGuidePurchase(transaction("Иванов Иван", "+79001234567", "buyer@mail.ru"));

        verify(amoCrmGateway).createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID);
        verify(amoCrmGateway, never()).updateLeadStatus(anyLong(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway, never()).updateContactCustomField(anyLong(), any());
        verify(amoCrmGateway).setNewTask(IRINA_ID, REACH_OUT_TASK_TYPE_ID,
                GuideAmoLeadServiceImpl.TASK_TEXT, 555L, THREE_DAYS_MINUTES);
    }

    @Test
    void failsWithoutAmoTaskWhenMoveToGuideBoughtFails() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru")).thenReturn(777L);
        when(amoCrmGateway.getLeadIdsByContact(777L)).thenReturn(List.of(10L));
        when(amoCrmGateway.getLead(10L)).thenReturn(lead(EDUCATION_PIPELINE_ID, 77900786L));
        when(amoCrmGateway.updateLeadStatus(10L, GUIDE_BOUGHT_STATUS_ID, EDUCATION_PIPELINE_ID, IRINA_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.pushGuidePurchase(transaction("Иванов Иван", "+79001234567", "buyer@mail.ru")));

        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    @Test
    void createsNewContactWhenNothingFoundBySearch() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID)).thenReturn(555L);
        when(amoCrmGateway.getContactIdFromLead(555L)).thenReturn(777L);

        service.pushGuidePurchase(transaction("Иванов Иван", "+79001234567", "buyer@mail.ru"));

        verify(amoCrmGateway).findContactIdByQuery("buyer@mail.ru");
        verify(amoCrmGateway).findContactIdByQuery("79001234567");
        verify(amoCrmGateway).createLead(GuideAmoLeadServiceImpl.LEAD_NAME, "Иванов Иван", "+79001234567",
                "buyer@mail.ru", EDUCATION_PIPELINE_ID, GUIDE_BOUGHT_STATUS_ID, IRINA_ID);
    }

    @Test
    void propagatesContactSearchFailure() {
        when(amoCrmGateway.findContactIdByQuery("buyer@mail.ru"))
                .thenThrow(new RuntimeException("amo down"));

        assertThrows(RuntimeException.class,
                () -> service.pushGuidePurchase(transaction("Иванов Иван", null, "buyer@mail.ru")));

        verify(amoCrmGateway, never()).createLead(anyString(), anyString(), any(), any(), anyLong(), anyLong(), anyLong());
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    @Test
    void doesNothingElseWhenAmoDisabled() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(null);

        service.pushGuidePurchase(transaction("Иванов Иван", null, "buyer@mail.ru"));

        verify(amoCrmGateway, never()).getContactIdFromLead(anyLong());
        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }

    @Test
    void propagatesLeadCreationFailure() {
        noExistingContactInAmo();
        when(amoCrmGateway.createLead(anyString(), anyString(), any(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("amo down"));

        assertThrows(RuntimeException.class,
                () -> service.pushGuidePurchase(transaction("Иванов Иван", null, "buyer@mail.ru")));

        verify(amoCrmGateway, never()).setNewTask(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }
}
