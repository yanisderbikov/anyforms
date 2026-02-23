package ru.anyforms.service.telegram.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.anyforms.dto.telegram.TelegramMessageDTO;
import ru.anyforms.repository.telegram.GetterAllTelegramChatIds;
import ru.anyforms.service.telegram.BotService;
import ru.anyforms.service.telegram.TelegramMessageSender;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class BotServiceImpl implements BotService {

    private final TelegramMessageSender telegramSender;
    private final GetterAllTelegramChatIds getterAllChatIds;

    @Override
    public void pushMessageToAll(TelegramMessageDTO message) {
        List<String> chatIds = getterAllChatIds.getAllChatIds();
        for (String chatId : chatIds) {
            telegramSender.sendTextMessage(chatId, message);
        }
    }
}
