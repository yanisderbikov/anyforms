package ru.anyforms.model.amo;

import java.util.List;

public record AmoChatMessages(String chatId, List<AmoChatMessage> messages, boolean hasMore) {
}
