package ru.anyforms.service.telegram.init;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.anyforms.dto.telegram.Button;
import ru.anyforms.dto.telegram.TelegramMessageDTO;
import ru.anyforms.service.telegram.TelegramMessageSender;

import java.util.List;

@Component
class TelegramBotSenderImpl extends TelegramBotInitializer implements TelegramMessageSender {

    @Override
    public void sendTextMessage(String chatId, String message) {
        SendMessage sendMessage = new SendMessage(chatId, message);
        sendBaseMessage(sendMessage);
    }

    @Override
    public void sendTextMessage(String chatId, TelegramMessageDTO message) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId)
                .text(message.getMessage());

        List<Button> buttons = message.getButtons();
        if (buttons != null && !buttons.isEmpty()) {
            List<InlineKeyboardButton> row = buttons.stream()
                    .map(b -> InlineKeyboardButton.builder()
                            .text(b.getLabel())
                            .url(b.getUrl())
                            .build())
                    .toList();
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(row));
            builder.replyMarkup(keyboard);
        }

        sendBaseMessage(builder.build());
    }
}
