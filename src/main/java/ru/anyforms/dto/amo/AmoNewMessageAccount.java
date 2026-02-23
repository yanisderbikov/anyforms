package ru.anyforms.dto.amo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AmoNewMessageAccount {
    private String subdomain;
    private Long id;

    @JsonProperty("self_link")
    private String selfLink;
}
