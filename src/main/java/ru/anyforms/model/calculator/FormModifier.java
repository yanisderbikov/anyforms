package ru.anyforms.model.calculator;

import lombok.Getter;

@Getter
public enum FormModifier {
    DISPLACER_DIFFUSER("Вытеснитель «диффузор»",
            "Сплошной силиконовый вытеснитель: +1 заливка, его вес добавьте в вес силикона."),
    DISPLACER_TREE("Вытеснитель «ёлка»",
            "+1 заливка: пластиковый крест к каждой форме — в вес оснастки, чехол 6 мм — в вес силикона.");

    private final String label;
    private final String hint;

    FormModifier(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }
}
