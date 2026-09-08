package ru.anyforms.dto.salesbot;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Статистика одного шага цепочки за период")
public record BotAnalyticsStepDTO(
        @Schema(description = "Позиция в цепочке (0 — ручной запуск)") Integer position,
        @Schema(description = "ID SalesBot в amoCRM") Long botId,
        @Schema(description = "Название бота из amoCRM; null — неизвестно") String botName,
        @Schema(description = "SUCCESS: запрос на запуск бота ушёл в amoCRM") long sent,
        @Schema(description = "MESSAGE_SEND_FAILED: бот запущен, но amoCRM не смог доставить сообщение — на этом шаге лид заблокировал/недоступен") long blocked,
        @Schema(description = "FAILED: бот не запущен (лид вышел из статуса или ошибка запроса)") long failed,
        @Schema(description = "Доля заблокированных среди запущенных: blocked / (sent + blocked); null — запусков не было") Double blockedShare,
        @Schema(description = "Есть ли этот бот на этой позиции в текущей цепочке bot_sequence") boolean inSequence
) {
}
