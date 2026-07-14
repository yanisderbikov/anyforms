package ru.anyforms.dto.telegram;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Подтверждение от telegram-pusher: сообщение по этим заказам отправлено. */
@Data
@Schema(description = "Подтверждение отправки дайджеста")
public class TelegramDigestConfirmRequestDTO {

    @NotEmpty
    @Schema(description = "ID заказов из полученного дайджеста")
    private List<Long> orderIds;
}
