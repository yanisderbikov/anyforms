package ru.anyforms.model.payment;

public enum Currency {
    RUB("RUB", "Российский рубль"),
    USD("USD", "Доллар США"),
    EUR("EUR", "Евро");

    private final String code;
    private final String description;

    Currency(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Currency fromCode(String code) {
        for (Currency currency : values()) {
            if (currency.code.equals(code)) {
                return currency;
            }
        }
        throw new IllegalArgumentException("Неизвестный код валюты: " + code);
    }
}
