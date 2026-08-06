package ru.anyforms.model.task;

import ru.anyforms.dto.amo.CourseAmoLeadTaskPayload;
import ru.anyforms.dto.amo.GuideAmoLeadTaskPayload;
import ru.anyforms.dto.email.EmailTaskPayload;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;
import ru.anyforms.dto.email.ReceiptEmailTaskPayload;

public enum TaskType {
    /** Письмо о покупке курса/гайда. */
    EMAIL(EmailTaskPayload.class),
    /** Письмо-чек заказа маркетплейса. */
    MARKETPLACE_ORDER_EMAIL(MarketplaceOrderEmailPayload.class),
    /** Письмо со ссылкой на чек Юкассы. */
    RECEIPT_EMAIL(ReceiptEmailTaskPayload.class),
    AMO_GUIDE_LEAD(GuideAmoLeadTaskPayload.class),
    AMO_COURSE_BOUGHT(CourseAmoLeadTaskPayload.class);

    private final Class<?> payloadClass;

    TaskType(Class<?> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<?> getPayloadClass() {
        return payloadClass;
    }

    public static TaskType fromObject(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("Request не может быть null");
        }
        Class<?> requestClass = request.getClass();
        for (TaskType taskType : values()) {
            if (taskType.payloadClass.isAssignableFrom(requestClass)) {
                return taskType;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип таски для класса: " + requestClass.getName());
    }
}
