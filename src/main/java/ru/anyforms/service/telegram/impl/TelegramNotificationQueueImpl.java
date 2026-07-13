package ru.anyforms.service.telegram.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.model.TelegramNotification;
import ru.anyforms.repository.TelegramNotificationRepository;
import ru.anyforms.service.telegram.TelegramNotificationQueue;

@Slf4j
@Service
@RequiredArgsConstructor
class TelegramNotificationQueueImpl implements TelegramNotificationQueue {

    private final TelegramNotificationRepository repository;

    @Override
    @Transactional
    public void enqueue(Long orderId) {
        if (orderId == null || repository.existsByOrderId(orderId)) {
            return;
        }
        TelegramNotification notification = new TelegramNotification();
        notification.setOrderId(orderId);
        repository.save(notification);
        log.info("Telegram notification enqueued for order #{}", orderId);
    }
}
