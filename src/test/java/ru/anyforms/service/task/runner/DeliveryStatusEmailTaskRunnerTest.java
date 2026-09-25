package ru.anyforms.service.task.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.anyforms.model.task.Task;
import ru.anyforms.model.task.TaskStatus;
import ru.anyforms.model.task.TaskType;
import ru.anyforms.repository.GetterTaskByStatus;
import ru.anyforms.repository.SaverTask;
import ru.anyforms.service.email.EmailService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryStatusEmailTaskRunnerTest {

    private final GetterTaskByStatus getterTaskByStatus = mock(GetterTaskByStatus.class);
    private final EmailService emailService = mock(EmailService.class);
    private final SaverTask saverTask = mock(SaverTask.class);
    private final DeliveryStatusEmailTaskRunner runner =
            new DeliveryStatusEmailTaskRunner(getterTaskByStatus, emailService, saverTask);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(runner, "batchSize", 10);
        ReflectionTestUtils.setField(runner, "supportPhone", "+79810403953");
    }

    private static Task task(String payload) {
        return Task.builder()
                .id(UUID.randomUUID())
                .type(TaskType.DELIVERY_STATUS_EMAIL)
                .status(TaskStatus.NEW)
                .payload(payload)
                .build();
    }

    private Task run(String payload) {
        Task task = task(payload);
        when(getterTaskByStatus.getByTaskTypeAndStatus(TaskType.DELIVERY_STATUS_EMAIL, TaskStatus.NEW, 10))
                .thenReturn(List.of(task));
        runner.runBatch();
        return task;
    }

    @Test
    void shippedEmailContainsTrackerPvzAndSupportContacts() {
        Task task = run("{\"to\":\"buyer@mail.ru\",\"notification\":\"SHIPPED\",\"orderPublicId\":\"ab12cd\","
                + "\"customerName\":\"Иван\",\"tracker\":\"1234567890\",\"pvzCity\":\"Москва\",\"pvzStreet\":\"ул. Ленина, 1\","
                + "\"supportTelegram\":\"AnyFormsBot\",\"shopSlug\":\"anyforms\",\"shopName\":\"anyforms\"}");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("buyer@mail.ru"), eq("Заказ #AB12CD передан в СДЭК"), html.capture(), eq(null));
        String body = html.getValue();
        assertTrue(body.contains("1234567890"));
        assertTrue(body.contains("https://www.cdek.ru/ru/tracking?order_id=1234567890"));
        assertTrue(body.contains("Москва, ул. Ленина, 1"));
        assertTrue(body.contains("Здравствуйте, Иван!"));
        assertTrue(body.contains("https://t.me/AnyFormsBot"));
        assertTrue(body.contains("tel:+79810403953"));
        assertTrue(body.contains("+7 981 040-39-53"));
        assertTrue(body.contains("Max, WhatsApp"));
        assertFalse(body.matches("(?s).*%[A-Z_]+%.*"));
        assertEquals(TaskStatus.DONE, task.getStatus());
    }

    @Test
    void arrivedEmailFromPartnerShopUsesShopBrandAndBot() {
        run("{\"to\":\"buyer@mail.ru\",\"notification\":\"ARRIVED_AT_PVZ\",\"orderPublicId\":\"ab12cd\","
                + "\"tracker\":\"1234567890\",\"pvzCity\":\"Москва\",\"pvzStreet\":\"ул. Ленина, 1\","
                + "\"supportTelegram\":\"AfPastryBot\",\"shopSlug\":\"af_pastry\",\"shopName\":\"AF Pastry\"}");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("buyer@mail.ru"), eq("Заказ #AB12CD ждёт вас в пункте выдачи"), html.capture(),
                eq("Команда AF Pastry"));
        assertTrue(html.getValue().contains("https://t.me/AfPastryBot"));
        assertTrue(html.getValue().contains("Здравствуйте!"));
    }

    @Test
    void pickupEmailHasNoTrackerAndLeadsToTelegram() {
        run("{\"to\":\"buyer@mail.ru\",\"notification\":\"READY_FOR_PICKUP\",\"orderPublicId\":\"ab12cd\","
                + "\"customerName\":\"Иван\",\"supportTelegram\":\"AnyFormsBot\",\"shopSlug\":\"anyforms\"}");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("buyer@mail.ru"), eq("Заказ #AB12CD готов к выдаче"), html.capture(), eq(null));
        assertFalse(html.getValue().contains("cdek.ru/ru/tracking"));
        assertTrue(html.getValue().contains("Написать в Telegram"));
    }

    @Test
    void missingNotificationFailsTask() {
        Task task = run("{\"to\":\"buyer@mail.ru\",\"orderPublicId\":\"ab12cd\"}");

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), any());
        assertEquals(TaskStatus.FAILED, task.getStatus());
    }
}
