package ru.anyforms.model.calculator;

import lombok.Getter;

@Getter
public enum FormType {
    STOCKING("Чулок",
            "Стенка 5 мм (крупные — 6). Кожух, если высота больше 50–60 мм или тяжёлая заливка.",
            false, ShellRule.TALL_OR_HEAVY_POUR, 1),
    STOCKING_CUT("Чулок с разрезом",
            "Стенка 6 мм + ребро 10 мм, кожух обязателен, продольный разрез.",
            true, ShellRule.ALWAYS, 1),
    BRICK("Кирпич (брусок)",
            "Отступ 4 мм от габаритов (крупные ~10). Кожух только у тяжёлых; фанера и ЧПУ — в доплаты к форме.",
            false, ShellRule.HEAVY_FORM, 1),
    CYLINDER("Цилиндр",
            "Отступ от максимального диаметра, опора — как у кирпича.",
            false, ShellRule.HEAVY_FORM, 1),
    CUP("Стакан",
            "Стенка 6–7 мм, кожух. Сердечник одним молдом; у крупных пластиковый сердечник к каждой форме — в вес оснастки.",
            false, ShellRule.ALWAYS, 1),
    RELIEF("Рельеф",
            "Стенка 8 мм, шире к основанию, без кожуха.",
            false, ShellRule.NEVER, 1),
    FLAT_MATRIX_BAR("Плоская матрица — брусок",
            "Дно и стенки по 4 мм. Сборка матрицы — в подготовку, дубли ячеек — в копию.",
            false, ShellRule.NEVER, 1),
    FLAT_MATRIX_CLUSTER("Плоская матрица — кластер",
            "Стенка 5 мм по контуру. Цена «950 + 350 ₽ за ячейку» — только как исключение основателя.",
            false, ShellRule.NEVER, 1),
    COMPOSITE("Составная форма матрицами",
            "Две матрицы и две литьевые оснастки, опора на каждую половину, 2 заливки. Для платины — 2 промежуточные формы.",
            false, ShellRule.ALWAYS, 2),
    SET("Комплект из нескольких форм",
            "Веса силикона и оснастки — суммой, промежуточных форм — по числу форм, вторая копия — в подготовку.",
            false, ShellRule.TALL_OR_HEAVY_POUR, 1);

    private final String label;
    private final String hint;
    private final boolean cut;
    private final ShellRule shellRule;
    private final int basePours;

    FormType(String label, String hint, boolean cut, ShellRule shellRule, int basePours) {
        this.label = label;
        this.hint = hint;
        this.cut = cut;
        this.shellRule = shellRule;
        this.basePours = basePours;
    }

    public int pours(Integer setFormsCount) {
        if (this == SET) {
            return setFormsCount == null ? 2 : Math.max(setFormsCount, 1);
        }
        return basePours;
    }

    public int defaultTinForms(Integer setFormsCount) {
        if (this == COMPOSITE) {
            return 2;
        }
        if (this == SET) {
            return setFormsCount == null ? 2 : Math.max(setFormsCount, 1);
        }
        return 1;
    }

    public enum ShellRule {
        ALWAYS,
        NEVER,
        TALL_OR_HEAVY_POUR,
        HEAVY_FORM
    }
}
