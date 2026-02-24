package ru.anyforms.dto.amo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AmoNewMessageItem {
    private String id;
    private String type;
    private String text;

    @JsonProperty("created_at")
    private Long createdAt;

    private String origin;

    @JsonProperty("chat_id")
    private String chatId;

    @JsonProperty("talk_id")
    private String talkId;

    @JsonProperty("contact_id")
    private Long contactId;

    private AmoNewMessageEntity entity;
    private AmoNewMessageAuthor author;
}
