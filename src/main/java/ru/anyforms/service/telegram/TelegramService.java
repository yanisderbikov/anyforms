package ru.anyforms.service.telegram;

public interface TelegramService {
    boolean isConfigured();

    void sendMessage(String text);

    void sendMessageWithUrlButton(String text, String buttonText, String buttonUrl);
}
