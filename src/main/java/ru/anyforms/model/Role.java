package ru.anyforms.model;

public enum Role {
    ADMIN,
    SALES_MANAGER,
    PROJECT_MANAGER,
    CLIENT,
    /** Не человек, а другой сервис: telegram-pusher, платформа обучения, технические ручки */
    SERVICE
}
