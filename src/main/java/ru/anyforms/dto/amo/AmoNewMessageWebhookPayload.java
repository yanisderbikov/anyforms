package ru.anyforms.dto.amo;

import lombok.Data;

@Data
public class AmoNewMessageWebhookPayload {
    private AmoNewMessageAccount account;
    /** Одно сообщение (из message.add[0] в вебхуке). */
    private AmoNewMessageItem message;
}
