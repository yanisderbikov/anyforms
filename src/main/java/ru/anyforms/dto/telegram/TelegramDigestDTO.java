package ru.anyforms.dto.telegram;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Дайджест непрочитанных телеграм-уведомлений для сервиса telegram-pusher. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Дайджест уведомлений для telegram-pusher")
public class TelegramDigestDTO {

    @Schema(description = "ID заказов, вошедших в дайджест (передать обратно в /confirm)")
    private List<Long> orderIds;

    @Schema(description = "Готовый текст сообщения (null, если уведомлений нет)")
    private String text;

    @Schema(description = "Текст кнопки")
    private String buttonText;

    @Schema(description = "Ссылка кнопки")
    private String buttonUrl;
}
