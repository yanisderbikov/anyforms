package ru.anyforms.repository.telegram;

public interface SaverTelegramSubscriber {

    void saveIfNotExist(String chatId, String username);
    default void saveIfNotExist(Long chatId, String username) {
        saveIfNotExist(chatId.toString(), username);
    }
}
