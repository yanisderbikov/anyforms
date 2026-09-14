package ru.anyforms.model;

public enum Role {
    ADMIN,
    SALES_MANAGER,
    PROJECT_MANAGER,
    /** Владелец магазина-партнёра: видит только аналитику продаж своего магазина */
    SHOP_OWNER,
    /** Не человек, а другой сервис: telegram-pusher, платформа обучения, технические ручки */
    SERVICE;

    public boolean isAdminPanelRole() {
        return this == ADMIN || this == SALES_MANAGER || this == PROJECT_MANAGER || this == SHOP_OWNER;
    }
}
