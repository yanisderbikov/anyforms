package ru.anyforms.model;

public enum DeliveryMethod {
    CDEK("СДЭК"),
    PICKUP("Самовывоз");

    private final String description;

    DeliveryMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
