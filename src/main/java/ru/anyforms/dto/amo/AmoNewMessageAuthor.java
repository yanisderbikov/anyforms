package ru.anyforms.dto.amo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AmoNewMessageAuthor {
    private String id;
    private String type;
    private String name;

    @JsonProperty("avatar_url")
    private String avatarUrl;
}
