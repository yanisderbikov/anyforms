package ru.anyforms.dto.amo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AmoNewMessageEntity {
    private String type;
    private Long id;
}
