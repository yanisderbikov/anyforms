package ru.anyforms.model.task;

import ru.anyforms.dto.email.EmailTaskPayload;
import ru.anyforms.dto.email.MarketplaceOrderEmailPayload;

public enum TaskType {
    /** Письмо о покупке курса/гайда. */
    EMAIL(EmailTaskPayload.class),
    /** Письмо-чек заказа маркетплейса. */
    MARKETPLACE_ORDER_EMAIL(MarketplaceOrderEmailPayload.class);

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
