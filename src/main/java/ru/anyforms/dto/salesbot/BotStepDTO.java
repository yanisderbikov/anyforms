package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.salesbot.BotSequence;
import ru.anyforms.model.salesbot.OrderType;

import java.util.Map;

@Schema(description = "Шаг цепочки: бот на позиции для типа заказа (bot_sequence)")
public record BotStepDTO(
        Long id,
        OrderType type,
        @Schema(description = "Позиция в цепочке, с 1") Integer position,
        @Schema(description = "ID SalesBot в amoCRM") Long botId,
        @Schema(description = "Название бота из amoCRM; null — список ботов недоступен или бот удалён") String botName
) {
    public static BotStepDTO from(BotSequence step, Map<Long, String> botNames) {
        return new BotStepDTO(step.getId(), step.getType(), step.getPosition(), step.getBotId(),
                botNames.get(step.getBotId()));
    }
}
