package ru.anyforms.service.task.runner;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.dto.promo.CoursePromoDTO;
import ru.anyforms.dto.promo.CoursePromoOutcome;
import ru.anyforms.model.task.Task;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;
import ru.anyforms.service.promo.CoursePromoService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceOrderEmailTaskRunnerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final CoursePromoService coursePromoService = mock(CoursePromoService.class);
    private final MarketplaceOrderEmailTaskRunner runner = new MarketplaceOrderEmailTaskRunner(
            mock(GetterTaskByStatus.class), emailService, coursePromoService, mock(SaverTask.class));

    @Test
    void emailCarriesCourseOffer() {
        when(coursePromoService.issueForOrder("9MMN3O")).thenReturn(CoursePromoOutcome.ready(offer()));

        runner.process(task());

        assertTrue(sentHtml().contains("RETAIL-SWB5G"));
    }

    @Test
    void emailWithoutOfferWhenNothingIssued() {
        when(coursePromoService.issueForOrder("9MMN3O")).thenReturn(CoursePromoOutcome.none());

        runner.process(task());

        assertFalse(sentHtml().contains("Подарок к заказу"));
    }

    @Test
    void receiptIsSentEvenIfIssuingFails() {
        when(coursePromoService.issueForOrder("9MMN3O")).thenThrow(new IllegalStateException("db"));

        runner.process(task());

        assertFalse(sentHtml().contains("Подарок к заказу"));
    }

    private String sentHtml() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("buyer@mail.ru"), any(), html.capture(), any());
        return html.getValue();
    }

    private static Task task() {
        Task task = new Task();
        task.setPayload(new Gson().toJson(MarketplaceOrderEmailPayload.builder()
                .to("buyer@mail.ru")
                .orderPublicId("9MMN3O")
                .totalRub("4300.00")
                .shopSlug("anyforms")
                .items(List.of(MarketplaceOrderEmailPayload.Item.builder()
                        .name("мышиный король").quantity(1).priceRub("4300.00").build()))
                .build()));
        return task;
    }

    private static CoursePromoDTO offer() {
        return new CoursePromoDTO(UUID.randomUUID(), "Скидка на курс", "на курс", "текст", "Открыть",
                "RETAIL-SWB5G", "COURSE_STREAM_2", 430_000L, 1_690_000L, 1_260_000L,
                "2026-11-07T21:00:00Z", 90, "9212", false);
    }
}
