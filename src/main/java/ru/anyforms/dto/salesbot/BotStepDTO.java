package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.BotSequence;

import java.util.Map;

@Schema(description = "Шаг цепочки: бот на позиции в группе (bot_sequence)")
public record BotStepDTO(
        Long id,
        Long groupId,
        @Schema(description = "Позиция в цепочке, с 1") Integer position,
        @Schema(description = "Не раньше чем через N минут после предыдущего шага (для шага 1 — после попадания сделки в статус группы)") Integer delayMinutes,
        @Schema(description = "ID SalesBot в amoCRM") Long botId,
        @Schema(description = "Название бота из amoCRM; null — список ботов недоступен или бот удалён") String botName
) {
    public static BotStepDTO from(BotSequence step, Map<Long, String> botNames) {
        return new BotStepDTO(step.getId(), step.getGroupId(), step.getPosition(), step.getDelayMinutes(), step.getBotId(),
                botNames.get(step.getBotId()));
    }
}
