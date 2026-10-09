package ru.anyforms.dto.amo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AmoReplyCheckTaskPayload {
    private Long leadId;
    private String chatId;
    private Long contactId;
}
