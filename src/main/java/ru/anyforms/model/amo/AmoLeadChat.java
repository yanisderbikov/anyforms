package ru.anyforms.model.amo;

import java.util.List;

public record AmoLeadChat(AmoTalk talk, List<AmoChatMessage> messages, boolean hasMore) {
}
