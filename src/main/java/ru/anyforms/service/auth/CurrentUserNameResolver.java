package ru.anyforms.service.auth;

import java.util.Optional;

/** Имя пользователя, от которого идёт текущий запрос (для подписи «кто изменил»). */
public interface CurrentUserNameResolver {

    /**
     * Имя из профиля, если не задано — email. Пусто, если запрос не от пользователя
     * (планировщик, межсервисный токен) или вызвано вне потока запроса (например, в @Async).
     */
    Optional<String> currentUserName();
}
