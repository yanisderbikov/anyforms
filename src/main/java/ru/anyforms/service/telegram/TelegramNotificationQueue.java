package ru.anyforms.service.telegram;

public interface TelegramNotificationQueue {

    void enqueue(Long orderId);
}
