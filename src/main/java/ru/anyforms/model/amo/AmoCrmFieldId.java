package ru.anyforms.model.amo;

/**
 * Enum для хранения ID кастомных полей amoCRM
 */
public enum AmoCrmFieldId {
    // Поля для трекинга и доставки
    TRACKER(2348069L, "Трекер"),
    DELIVERY_STATUS(2601105L, "Статус доставки"),

    RELEASED_AT(2757837L, "Завершен заказ"),
    COUNT_RELEASED_ORDERS(2438411L, "Кол-во покупок"),

    // Поля для контактов
    FIO(2449809L, "ФИО"),
    PHONE(2265635L, "Телефон"),
    CONTACT_PVZ_STREET(2370939L, "ПВЗ СДЭК улица"),
    CONTACT_PVZ_CITY(2331841L, "ПВЗ СДЭК город"),

    // Поля для сделок
    PRODUCT_TYPE(2482683L, "Тип продукта (мультисписок)"),
    RETAIL(2454667L, "Розница"),
    DATE_PAYMENT(2364807L, "Дата оплаты"),
    QUANTITY(2351399L, "Количество"),
    @Deprecated
    HORSE_COUNT(2351399L, "Количество лошадок"), // То же поле что и QUANTITY
    FORMS_COUNT(2351399L, "Количество форм"), // То же поле что и QUANTITY

    COMMENT_TO_ORDER(2625137L, "Комментарий к заказу"), // То же поле что и QUANTITY
    
    // Поля для расчетов
    MIN_FORMS_COUNT(2337779L, "Мин-кол-во форм"),
    FORM_PRICE(2337773L, "Форма"),
    PROJECT_PRICE(2337775L, "Проект");
    
    private final Long id;
    private final String description;
    
    AmoCrmFieldId(Long id, String description) {
        this.id = id;
        this.description = description;
    }
    
    public Long getId() {
        return id;
    }
    
    public String getDescription() {
        return description;
    }
}
