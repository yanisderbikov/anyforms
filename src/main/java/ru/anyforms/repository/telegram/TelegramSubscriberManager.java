package ru.anyforms.repository.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.anyforms.model.telegram.TelegramSubscriber;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class TelegramSubscriberManager implements GetterAllTelegramChatIds, SaverTelegramSubscriber {

    private final TelegramSubscriberRepository repository;

    @Override
    public List<String> getAllChatIds() {
        try {
            return repository.findAllChatIds();
        } catch (Exception e) {
            log.error("Ошибка в Telegram репозитории ", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveIfNotExist(String chatId, String username) {
        if (repository.findById(chatId).isEmpty()) {
            var subscriber = new TelegramSubscriber();
            subscriber.setChatId(chatId);
            subscriber.setUsername(username);
            repository.save(subscriber);
            log.debug("Registered Telegram subscriber: chatId={}", chatId);
        } else {
            log.trace("Telegram subscriber already exists: chatId={}", chatId);
        }
    }
}
