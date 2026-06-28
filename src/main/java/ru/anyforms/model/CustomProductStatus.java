package ru.anyforms.model;

/**
 * Статус кастомной позиции под-заказа.
 */
public enum CustomProductStatus {
    MODELING("Моделирование"),
    IN_PRODUCTION("В производстве"),
    READY_TO_SHIP("Готов к отправке"),
    SENT("Отправлен");

    private final String description;

    CustomProductStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
