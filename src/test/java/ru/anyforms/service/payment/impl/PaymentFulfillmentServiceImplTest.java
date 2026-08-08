package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.dto.amo.CourseAmoLeadTaskPayload;
import ru.anyforms.dto.amo.GuideAmoLeadTaskPayload;
import ru.anyforms.dto.email.EmailTaskPayload;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.service.task.TaskAdder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentFulfillmentServiceImplTest {

    private final TaskAdder taskAdder = mock(TaskAdder.class);
    private final MarketplaceFulfillmentService marketplaceFulfillmentService = mock(MarketplaceFulfillmentService.class);
    private final PaymentFulfillmentServiceImpl service =
            new PaymentFulfillmentServiceImpl(taskAdder, marketplaceFulfillmentService);

    private static PaymentTransaction transaction(String productCode) {
        return PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .productCode(productCode)
                .email("buyer@mail.ru")
                .build();
    }

    @Test
    void guidePurchaseAddsEmailTaskAndAmoLeadTask() {
        PaymentTransaction transaction = transaction(PaymentProduct.CODE_GUIDE);

        service.fulfill(transaction);

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(taskAdder, times(2)).addTask(payloads.capture());
        List<Object> captured = payloads.getAllValues();

        EmailTaskPayload emailPayload = assertInstanceOf(EmailTaskPayload.class, captured.get(0));
        assertEquals("buyer@mail.ru", emailPayload.getTo());

        GuideAmoLeadTaskPayload amoPayload = assertInstanceOf(GuideAmoLeadTaskPayload.class, captured.get(1));
        assertEquals(transaction.getId(), amoPayload.getTransactionId());
    }

    @Test
    void coursePurchaseAddsEmailTaskAndCourseAmoTask() {
        PaymentTransaction transaction = transaction(PaymentProduct.CODE_COURSE);

        service.fulfill(transaction);

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(taskAdder, times(2)).addTask(payloads.capture());
        List<Object> captured = payloads.getAllValues();

        assertInstanceOf(EmailTaskPayload.class, captured.get(0));
        CourseAmoLeadTaskPayload amoPayload = assertInstanceOf(CourseAmoLeadTaskPayload.class, captured.get(1));
        assertEquals(transaction.getId(), amoPayload.getTransactionId());
    }

    @Test
    void personalCoursePurchaseAddsCourseAmoTask() {
        PaymentTransaction transaction = transaction(PaymentProduct.CODE_COURSE_PERSONAL);

        service.fulfill(transaction);

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(taskAdder, times(2)).addTask(payloads.capture());
        assertInstanceOf(CourseAmoLeadTaskPayload.class, payloads.getAllValues().get(1));
    }

    @Test
    void marketplaceCartDoesNotAddGuideTasks() {
        PaymentTransaction transaction = transaction(PaymentProduct.CODE_MARKETPLACE_CART);

        service.fulfill(transaction);

        verify(marketplaceFulfillmentService).fulfill(transaction);
        verify(taskAdder, never()).addTask(any());
    }
}
