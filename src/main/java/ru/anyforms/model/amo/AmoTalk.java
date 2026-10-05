package ru.anyforms.model.amo;

public record AmoTalk(
        Long talkId,
        String chatId,
        String origin,
        Long contactId,
        String entityType,
        Long entityId,
        String status,
        Boolean isRead,
        Long createdAt,
        Long updatedAt
) {
}
