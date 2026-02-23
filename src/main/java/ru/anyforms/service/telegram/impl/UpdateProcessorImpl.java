package ru.anyforms.service.telegram.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.anyforms.repository.telegram.SaverTelegramSubscriber;
import ru.anyforms.service.telegram.TelegramMessageSender;
import ru.anyforms.service.telegram.UpdateProcessor;


@RequiredArgsConstructor
@Component
class UpdateProcessorImpl implements UpdateProcessor {

    private final TelegramMessageSender telegramMessageSender;
    private final SaverTelegramSubscriber saverTelegramSubscriber;

    @Override
    public void processUpdate(Update update) {

        var text = update.getMessage().getText();
        var chatId = update.getMessage().getChatId();

        String responseMessage = "привет холоп";
        if (text != null && text.equals("привет") && getUsername(update).equals("yanderbikov")) {
            saverTelegramSubscriber.saveIfNotExist(chatId, getUsername(update));
            telegramMessageSender.sendTextMessage(chatId, responseMessage);
        }
    }

    private String getUsername(Update update) {
        var user = update.hasCallbackQuery() ? update.getCallbackQuery().getFrom() : update.getMessage().getFrom();
        return user.getUserName();
    }
}
