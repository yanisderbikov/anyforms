package ru.anyforms.model.task;

import ru.anyforms.dto.email.EmailTaskPayload;

/**
 * Тип фоновой таски. Каждый тип знает класс своего payload — по нему {@code TaskAdder}
 * определяет тип из объекта, а раннер десериализует payload обратно.
 */
public enum TaskType {
    EMAIL(EmailTaskPayload.class);

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
