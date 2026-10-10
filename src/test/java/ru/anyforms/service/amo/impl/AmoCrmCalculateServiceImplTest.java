package ru.anyforms.service.amo.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.model.amo.AmoCrmFieldId;
import ru.anyforms.model.amo.AmoLead;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmoCrmCalculateServiceImplTest {

    private static final long LEAD_ID = 777L;

    private final AmoCrmGateway amoCrmGateway = mock(AmoCrmGateway.class);
    private final AmoCrmCalculateServiceImpl service = new AmoCrmCalculateServiceImpl(amoCrmGateway);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseAmount", 25_000L);
        ReflectionTestUtils.setField(service, "marginProject", 0L);
        ReflectionTestUtils.setField(service, "marginForm", 0L);
        ReflectionTestUtils.setField(service, "minFormsCountLimit", 3L);
        when(amoCrmGateway.updateLeadFields(anyLong(), anyLong(), anyMap())).thenReturn(true);
    }

    @ParameterizedTest(name = "проект {0}, форма {1} → {2} форм, бюджет {3}")
    @CsvSource({
            "5000, 1000, 20, 25000",
            "10000, 2000, 8, 26000",
            "16000, 3000, 3, 25000",
            "10000, 8000, 3, 34000",
            "24000, 2000, 3, 30000",
            "40000, 5000, 3, 55000"
    })
    void takesWhicheverRuleNeedsMoreForms(long project, long form, long expectedForms, long expectedBudget) {
        givenLead(project, form);

        assertTrue(service.calculateAndUpdateLead(LEAD_ID));

        Map<Long, Object> fields = capturedFields(expectedBudget);
        assertEquals(String.valueOf(expectedForms), fields.get(AmoCrmFieldId.MIN_FORMS_COUNT.getId()));
        assertEquals(String.valueOf(expectedForms), fields.get(AmoCrmFieldId.FORMS_COUNT.getId()));
    }

    @Test
    void withoutMinFormsCountCalculatesByMinCheckOnly() {
        ReflectionTestUtils.setField(service, "minFormsCountLimit", 0L);
        givenLead(40_000, 5_000);

        assertTrue(service.calculateAndUpdateLead(LEAD_ID));

        Map<Long, Object> fields = capturedFields(40_000L);
        assertEquals("0", fields.get(AmoCrmFieldId.FORMS_COUNT.getId()));
    }

    private void givenLead(long project, long form) {
        AmoLead lead = mock(AmoLead.class);
        when(lead.getCustomFieldValue(AmoCrmFieldId.PROJECT_PRICE.getId())).thenReturn(String.valueOf(project));
        when(lead.getCustomFieldValue(AmoCrmFieldId.FORM_PRICE.getId())).thenReturn(String.valueOf(form));
        when(amoCrmGateway.getLead(LEAD_ID)).thenReturn(lead);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> capturedFields(long expectedBudget) {
        ArgumentCaptor<Map<Long, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(amoCrmGateway).updateLeadFields(eq(LEAD_ID), eq(expectedBudget), fields.capture());
        return fields.getValue();
    }
}
