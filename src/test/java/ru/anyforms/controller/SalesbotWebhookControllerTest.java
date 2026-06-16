package ru.anyforms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import ru.anyforms.service.salesbot.MessageDeliveryFailureHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Тесты разбора lead_id из вебхука fail-send-message (форматы amo и плоские ключи).
 */
class SalesbotWebhookControllerTest {

    private final MessageDeliveryFailureHandler handler = mock(MessageDeliveryFailureHandler.class);
    private final SalesbotWebhookController controller = new SalesbotWebhookController(handler);

    @Test
    void movesLead_fromAmoStandardForm() {
        when(handler.onSendFailed(50399385L)).thenReturn(true);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("leads[add][0][id]", "50399385");
        form.add("leads[add][0][status_id]", "86451842");
        form.add("leads[add][0][pipeline_id]", "10557858");
        form.add("account[subdomain]", "anyforms");

        ResponseEntity<String> r = controller.failSendMessage(form, null);

        assertEquals(200, r.getStatusCode().value());
        verify(handler).onSendFailed(50399385L);
    }

    @Test
    void movesAllLeads_whenSeveralInWebhook() {
        when(handler.onSendFailed(anyLong())).thenReturn(true);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("leads[add][0][id]", "111");
        form.add("leads[status][1][id]", "222");
        form.add("leads[update][0][id]", "333");

        controller.failSendMessage(form, null);

        verify(handler).onSendFailed(111L);
        verify(handler).onSendFailed(222L);
        verify(handler).onSendFailed(333L);
    }

    @Test
    void movesLead_fromFlatKey() {
        when(handler.onSendFailed(777L)).thenReturn(true);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("lead_id", "777");

        controller.failSendMessage(form, null);

        verify(handler).onSendFailed(777L);
    }

    @Test
    void movesLead_fromRawUrlEncodedBody() {
        when(handler.onSendFailed(50399385L)).thenReturn(true);
        // Тело с неверным Content-Type: form-строка не попала в formData.
        String body = "leads%5Badd%5D%5B0%5D%5Bid%5D=50399385&account%5Bsubdomain%5D=anyforms";

        controller.failSendMessage(null, body);

        verify(handler).onSendFailed(50399385L);
    }

    @Test
    void returns400_andDoesNotCallHandler_whenNoLeadId() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("account[subdomain]", "anyforms");

        ResponseEntity<String> r = controller.failSendMessage(form, null);

        assertEquals(400, r.getStatusCode().value());
        assertTrue(r.getBody() != null && r.getBody().contains("not found"));
        verifyNoInteractions(handler);
    }
}
