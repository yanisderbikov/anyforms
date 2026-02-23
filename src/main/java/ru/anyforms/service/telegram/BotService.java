package ru.anyforms.service.telegram;


import ru.anyforms.dto.telegram.TelegramMessageDTO;

public interface BotService {

    /**
     * Sends the given message to all registered Telegram subscribers.
     * Failures for individual chats are logged; the method does not throw.
     */
    void pushMessageToAll(TelegramMessageDTO message);
}
