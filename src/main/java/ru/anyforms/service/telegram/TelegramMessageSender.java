package ru.anyforms.service.telegram;


import ru.anyforms.dto.telegram.TelegramMessageDTO;

public interface TelegramMessageSender {
    void sendTextMessage(String chatId, String message);
    void sendTextMessage(String chatId, TelegramMessageDTO message);
    default void sendTextMessage(Long chatId, String message) {
        sendTextMessage(chatId.toString(), message);
    }
}
