package ru.anyforms.model.calculator;

import lombok.Getter;

@Getter
public enum PourMaterial {
    WAX("воск", false, false),
    GYPSUM("гипс", true, false),
    CONCRETE("бетон", true, false),
    JESMONITE("джесмонит", true, false),
    FOOD("пищевое", false, true),
    PLASTIC("пластик", false, false);

    private final String label;
    private final boolean heavy;
    private final boolean platinumOnly;

    PourMaterial(String label, boolean heavy, boolean platinumOnly) {
        this.label = label;
        this.heavy = heavy;
        this.platinumOnly = platinumOnly;
    }
}
