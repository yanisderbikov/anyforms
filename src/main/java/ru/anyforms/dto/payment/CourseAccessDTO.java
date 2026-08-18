package ru.anyforms.dto.payment;

/**
 * Доступ почты к платформе обучения.
 *
 * @param hasAccess   есть ли оплаченная покупка курса
 * @param plan        SELF (тариф «Самостоятельное изучение») или PERSONAL («Личное ведение»); null — доступа нет
 * @param productCode исходный код продукта: COURSE / COURSE_PERSONAL
 */
public record CourseAccessDTO(boolean hasAccess, String plan, String productCode) {

    public static final String PLAN_SELF = "SELF";
    public static final String PLAN_PERSONAL = "PERSONAL";

    public static CourseAccessDTO denied() {
        return new CourseAccessDTO(false, null, null);
    }
}
