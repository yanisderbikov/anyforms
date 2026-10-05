package ru.anyforms.model.amo;

import java.time.Instant;

public record AmoChatMessage(
        String id,
        Instant createdAt,
        Direction direction,
        String author,
        AuthorType authorType,
        String type,
        String text,
        String media,
        String fileName,
        Integer duration,
        String error
) {

    public enum Direction {
        IN,
        OUT
    }

    public enum AuthorType {
        CLIENT,
        MANAGER,
        BOT
    }
}
