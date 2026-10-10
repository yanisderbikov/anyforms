package ru.anyforms.model.calculator;

import lombok.Getter;

@Getter
public enum SiliconeType {
    PLATINUM("платина", "Точнее, без усадки, не выделяет масло; дороже в разработке."),
    TIN("олово", "Дешевле в разработке, ресурс формы ниже.");

    private final String label;
    private final String description;

    SiliconeType(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
